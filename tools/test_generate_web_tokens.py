import json
import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import generate_web_tokens as bridge


class WebTokenBridgeTest(unittest.TestCase):
    def test_build_emits_reviewed_css_fonts_and_licenses(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            model = bridge.build(target)
            stylesheet = target / 'tokens.css'
            self.assertEqual(stylesheet.read_bytes(), bridge.GOLDEN.read_bytes())
            self.assertEqual(len(model['typography']), 29)
            self.assertEqual(model['layout']['drawerWidth'], 480)
            self.assertEqual(len(list((target / 'fonts').glob('*.woff2'))), 7)
            font_urls = re.findall(r'url\("([^"]+\.woff2)"\)', stylesheet.read_text(encoding='utf-8'))
            self.assertCountEqual(font_urls, [font['file'] for font in model['fonts']])
            for font_url in font_urls:
                self.assertTrue((stylesheet.parent / font_url).is_file(), font_url)
            self.assertEqual(len(list((target / 'fonts').glob('*-OFL.txt'))), 3)
            for theme, scheme in [('clair', 'light'), ('sombre', 'dark')]:
                self.assertEqual(model['themes'][theme]['color-scheme'], scheme)
                self.assertEqual(model['themes'][theme]['--voile'], 'rgba(15, 16, 18, 0.48)')
            self.assertEqual(model['common']['--duree-trace'], '1200ms')

    def test_frozen_values_and_theme_deltas_are_independently_guarded(self):
        with tempfile.TemporaryDirectory() as directory:
            candidate = Path(directory) / 'candidate.json'
            source = json.loads(bridge.SOURCE.read_text(encoding='utf-8'))
            source['color']['tokens'][0]['value']['clair'] = '#000000'
            candidate.write_text(json.dumps(source), encoding='utf-8')
            with patch.object(bridge, 'SOURCE', candidate):
                with self.assertRaisesRegex(ValueError, 'frozen CSS baseline'):
                    bridge.build(None)

            deltas = json.loads(bridge.DELTAS.read_text(encoding='utf-8'))
            deltas['scrim']['sombre'] = 'transparent'
            candidate.write_text(json.dumps(deltas), encoding='utf-8')
            with patch.object(bridge, 'DELTAS', candidate):
                with self.assertRaisesRegex(ValueError, 'golden fixture'):
                    bridge.build(None)

    def test_theme_colors_preserve_verified_text_and_control_contrast(self):
        def luminance(hex_color):
            rgb = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
            linear = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4
                      for value in rgb]
            return sum(weight * value for weight, value in zip((0.2126, 0.7152, 0.0722), linear))

        for theme, values in bridge.build(None)['themes'].items():
            for fg, bg, minimum in [('encre', 'surface', 4.5), ('sur-accent', 'accent', 4.5),
                                    ('filet-controle', 'surface', 3)]:
                with self.subTest(theme=theme, foreground=fg, background=bg):
                    dark, light = sorted(luminance(values[f'--{role}']) for role in (fg, bg))
                    self.assertGreaterEqual((light + 0.05) / (dark + 0.05), minimum)

#!/usr/bin/env python3
"""Build the Étaye web tokens from the frozen JSON and the reviewed ADR delta."""
import argparse
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECEIPT = ROOT / 'design/received/2026-11-03'
SOURCE = RECEIPT / 'design-system/tokens.json'
EXPORT = RECEIPT / 'export/etaye-design-system'
BASELINE = EXPORT / 'tokens/tokens.css'
DELTAS = ROOT / 'design/tokens/adr-014.json'
GOLDEN = ROOT / 'tools/fixtures/etaye-tokens.golden.css'


def normalize(source, deltas=None):
    theming = [theme['id'] for theme in source['color']['themes']]
    themes = {
        theme: {f"--{token['name']}": token['value'][theme]
                for group in ('color', 'shadow') for token in source[group]['tokens']}
        for theme in theming
    }
    common = {f"--{token['name']}": token['value']
              for group in ('spacing', 'radius', 'trait', 'cible', 'transition')
              for token in source[group]['tokens']}
    typography = [{'family': group['family'], **role}
                  for group in source['type']['groups'] for role in group['styles']]
    if deltas:
        for theme in theming:
            themes[theme]['color-scheme'] = deltas['colorScheme'][theme]
            themes[theme]['--voile'] = deltas['scrim'][theme]
        common.update({f'--{name}': value for name, value in deltas['motion'].items()})
        typography.extend(deltas['typography'])
    common.update({f'--font-{name}': family for name, family in source['type']['families'].items()})
    return {
        'themes': themes,
        'common': common,
        'fonts': source['type']['fonts'],
        'typography': typography,
        'layout': deltas['layout'] if deltas else {},
    }


def render_css(tokens, *, font_prefix=''):
    lines = ['/* Étaye — généré depuis tokens.json. Thème : <html data-theme="clair"> ou "sombre". */']
    for font in tokens['fonts']:
        lines.append('@font-face {{ font-family: "{family}"; src: url("{prefix}{file}") format("woff2"); '
                     'font-weight: {weight}; font-style: {style}; font-display: swap; }}'
                     .format(prefix=font_prefix, **font))
    for theme, properties in tokens['themes'].items():
        prefix = ':root, ' if theme == 'clair' else ''
        lines.append(f'{prefix}[data-theme="{theme}"] {{')
        lines.extend(f'  {name}: {value};' for name, value in properties.items())
        lines.append('}')
    lines.append(':root {')
    lines.extend(f'  {name}: {value};' for name, value in tokens['common'].items())
    lines.append('}')
    for role in tokens['typography']:
        rules = ('font-family: var(--font-{family}); font-size: {fontSize}; line-height: {lineHeight}; '
                 'font-weight: {fontWeight};').format(**role)
        if 'letterSpacing' in role:
            rules += f" letter-spacing: {role['letterSpacing']};"
        lines.append(f".{role['name']} {{ {rules} }}")
    return '\n'.join(lines) + '\n'


def build(target):
    source = json.loads(SOURCE.read_text(encoding='utf-8'))
    if render_css(normalize(source), font_prefix='../') != BASELINE.read_text(encoding='utf-8'):
        raise ValueError('Received tokens.json no longer reproduces the frozen CSS baseline')
    deltas = json.loads(DELTAS.read_text(encoding='utf-8'))
    model = normalize(source, deltas)
    css = render_css(model)
    if css != GOLDEN.read_text(encoding='utf-8'):
        raise ValueError('Generated token CSS differs from the reviewed ADR-014 golden fixture')
    if target is not None:
        target.mkdir(parents=True, exist_ok=True)
        (target / 'tokens.css').write_text(css, encoding='utf-8')
        for font in model['fonts']:
            relative_path = Path(font['file'])
            font_path = target / relative_path
            font_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(EXPORT / relative_path, font_path)
        for license_file in sorted((RECEIPT / 'licenses').glob('*-OFL.txt')):
            shutil.copyfile(license_file, target / 'fonts' / license_file.name)
    return model


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target-dir', type=Path)
    build(parser.parse_args().target_dir)

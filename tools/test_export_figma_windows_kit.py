"""Corruption, secret and preservation checks for the portable native handoff."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import export_figma_windows_kit as exporter


class WindowsKitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='etaye-windows-kit-test-')
        self.addCleanup(self.temp.cleanup)
        base = Path(self.temp.name)
        self.work, self.repo, self.artifact, self.fonts, self.output = [base / p for p in ('work', 'repo', 'artifact', 'fonts', 'out')]
        for p in (self.work / 'scenes', self.work / 'calls', self.work / 'results', self.fonts, self.output,
                  self.repo / 'design/figma-windows-import', self.repo / 'design/figma-local-import',
                  self.repo / 'docs/design/windows-handoff', self.artifact / 'review', self.artifact / 'site'):
            p.mkdir(parents=True, exist_ok=True)
        for name in ('START-HERE.md', 'RESUME-PROMPT.txt', 'VERIFY-KIT.ps1'):
            (self.repo / 'docs/design/windows-handoff' / name).write_text('Fixture instructions\n')
        (self.repo / 'design/figma-local-import/font-sources.json').write_text('[]')
        (self.repo / 'design/figma-windows-import/code.js').write_text('/* Fixture plugin */')
        (self.repo / 'README.md').write_text('Keep exact source bytes.\n')
        (self.artifact / 'site/index.html').write_text('<p>Synthetic fixture</p>')
        file_sha = exporter.sha((self.artifact / 'site/index.html').read_bytes())
        (self.artifact / 'review/corrected-snapshot.json').write_text(json.dumps({'snapshotSha256': 'a' * 64, 'files': [{'path': 'site/index.html', 'sha256': file_sha}]}))
        families = [f'S-{i:02d}' for i in range(1, 41)] + [f'I-{i:02d}' for i in range(1, 29)] + [f'P-{i:02d}' for i in range(1, 4)] + [f'M-{i:02d}' for i in range(1, 6)]
        ids = [family + '/test-' + str(i) for family in families for i in range(6)]
        sources = []
        for i, sid in enumerate(ids):
            name = f'scene-{i}.json'
            payload = json.dumps({'id': sid, 'scene': {'width': 1440, 'height': 1024}}).encode()
            (self.work / 'scenes' / name).write_bytes(payload)
            sources.append({'id': sid, 'file': name, 'sha256': exporter.sha(payload)})
        plan, cursor = [], 0
        for i in range(94):
            amount = 5 if i < 80 else 4
            selected = ids[cursor:cursor + amount]
            cursor += amount
            name = f'packet-{i:03d}'
            plan.append({'name': name, 'states': selected})
            (self.work / 'calls' / (name + '.raw.json')).write_text(json.dumps({'snapshot': 'a' * 64, 'capture': 'b' * 64, 'frames': [{'id': sid, 'width': 1440, 'height': 1024} for sid in selected], 'nodes': []}))
        (self.work / 'MANIFEST.json').write_text(json.dumps({'sourceSnapshotSha256': 'a' * 64, 'captureSha256': 'b' * 64, 'states': sources, 'assets': {}}))
        (self.work / 'PLAN.json').write_text(json.dumps(plan))
        (self.repo / 'design/figma-windows-import/canonical-queue.json').write_text(json.dumps([
            {'states': item['states'], 'rawSha256': exporter.sha((self.work / 'calls' / (item['name'] + '.raw.json')).read_bytes())}
            for item in plan]))
        (self.work / 'EXISTING-FIGMA-STATE.json').write_text(json.dumps({'fileKey': 'fixture-file', 'pageId': '15:2', 'completedSections': [{'sectionId': '26:2', 'roots': [{'state': sid} for sid in ids[:8]]}], 'cleanupCandidates': []}))
        for name in ('export.py', 'compile.py', 'codec.py', 'render.js', 'capture-enhanced.js'):
            (self.work / name).write_text('/* Preserved preparation fixture */')
        self.addCleanup(patch.stopall)
        patch.object(exporter, 'ROOT', self.repo).start()

        def git_metadata(command, **kwargs):
            if command[1] == 'ls-files':
                return b'README.md\0'
            return 'c' * 40 + '\n' if command[1] == 'rev-parse' else ''
        patch.object(exporter.subprocess, 'check_output', side_effect=git_metadata).start()

    def test_complete_native_payload_and_archive_preserve_previous_exports(self):
        user = self.output / 'user-notes.txt'
        user.write_text('Keep this newer user work')
        first = exporter.export(self.work, self.artifact, self.fonts, self.output)
        previous = {str(p): p.read_bytes() for p in self.output.rglob('*') if p.is_file()}
        second = exporter.export(self.work, self.artifact, self.fonts, self.output)
        self.assertNotEqual(first['folder'], second['folder'])
        for name, data in previous.items():
            self.assertEqual(Path(name).read_bytes(), data)
        folder = Path(second['folder'])
        packets = [json.loads(p.read_text()) for p in (folder / 'batches').glob('*.json')]
        self.assertEqual(len(packets), 94)
        self.assertEqual(sum(len(p['frames']) for p in packets), 456)
        self.assertTrue(all(p['format'] == 'etaye-windows-native-v2' for p in packets))
        self.assertTrue(all('replaceId' not in p for p in packets))
        manifest = json.loads((folder / 'MANIFEST.json').read_text())
        self.assertFalse(manifest['nativeWindowsExecutionVerified'])
        with zipfile.ZipFile(second['archive']) as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(len(archive.namelist()), len(manifest['files']) + 1)
            for name, details in manifest['files'].items():
                data = archive.read(folder.name + '/' + name)
                self.assertEqual(hashlib.sha256(data).hexdigest(), details['sha256'])
                self.assertEqual(data, (folder / name).read_bytes())

    def test_corrupt_scene_or_existing_evidence_is_rejected_before_output(self):
        source = self.work / 'scenes/scene-0.json'
        original = source.read_bytes()
        source.write_text('Changed after capture')
        with self.assertRaisesRegex(ValueError, 'Scene integrity'):
            exporter.export(self.work, self.artifact, self.fonts, self.output)
        self.assertEqual(list(self.output.iterdir()), [])
        source.write_bytes(original)
        state = self.work / 'EXISTING-FIGMA-STATE.json'
        data = json.loads(state.read_text())
        data['completedSections'][0]['roots'][0]['state'] = 'S-99/not-in-source'
        state.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, 'Existing native state'):
            exporter.prepare_packets(self.work)

    def test_secret_source_is_blocked_and_failure_remains_marked_incomplete(self):
        (self.repo / 'README.md').write_text('fig' + 'd_' + 'X' * 32)
        with self.assertRaisesRegex(ValueError, 'Potential credential'):
            exporter.export(self.work, self.artifact, self.fonts, self.output)
        self.assertFalse(list(self.output.glob('Etaye-Figma-Windows-*')))
        self.assertTrue(list(self.output.glob('.etaye-windows-incomplete-*')))

    def test_repository_destination_and_changed_site_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'outside the repository'):
            exporter.export(self.work, self.artifact, self.fonts, self.repo / 'bad-output')
        (self.artifact / 'site/index.html').write_text('Modified source')
        with self.assertRaisesRegex(ValueError, 'complete snapshot'):
            exporter.export(self.work, self.artifact, self.fonts, self.output)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_changed_compiled_packet_and_dirty_repository_cannot_be_delivered(self):
        packet = self.work / 'calls/packet-093.raw.json'
        data = json.loads(packet.read_text())
        data['frames'][0]['node'] = 999
        packet.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, 'pinned compiled source'):
            exporter.prepare_packets(self.work)
        with patch.object(exporter.subprocess, 'check_output', side_effect=['c' * 40, '?? untracked-source.py\n']):
            with self.assertRaisesRegex(ValueError, 'Commit the complete source tree'):
                exporter.export(self.work, self.artifact, self.fonts, self.output)
        self.assertEqual(list(self.output.iterdir()), [])


if __name__ == '__main__':
    unittest.main()

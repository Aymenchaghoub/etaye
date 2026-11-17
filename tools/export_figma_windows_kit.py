#!/usr/bin/env python3
"""Package verified desktop scenes and the offline native importer for Windows."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SECRET = re.compile(rb'(?:figd_[A-Za-z0-9_-]{25,}|gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----)')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def prepare_packets(work):
    manifest = json.loads((work / 'MANIFEST.json').read_text(encoding='utf-8'))
    plan = json.loads((work / 'PLAN.json').read_text(encoding='utf-8'))
    state = json.loads((work / 'EXISTING-FIGMA-STATE.json').read_text(encoding='utf-8'))
    canonical = json.loads((ROOT / 'design/figma-windows-import/canonical-queue.json').read_text(encoding='utf-8'))
    sources = {row['id']: row for row in manifest['states']}
    if len(sources) != 456 or len(plan) != 94 or len({s.split('/')[0] for s in sources}) != 76:
        raise ValueError('Expected the complete reviewed 456-state / 94-packet export')
    existing = [r['state'] for section in state['completedSections'] for r in section['roots']]
    if len(existing) != 8 or len(set(existing)) != 8 or not set(existing).issubset(sources):
        raise ValueError('Existing native state evidence does not match the source set')
    for row in sources.values():
        path = work / 'scenes' / row['file']
        if path.is_symlink() or path.parent != work / 'scenes' or sha(path.read_bytes()) != row['sha256']:
            raise ValueError('Scene integrity mismatch: ' + row['id'])
    assets = {}
    for key, asset in manifest['assets'].items():
        data = Path(asset['path']).read_bytes()
        if sha(data) != key or not data.startswith(b'\x89PNG\r\n\x1a\n'):
            raise ValueError('PNG asset integrity mismatch')
        assets[key] = base64.b64encode(data).decode('ascii')
    packets, seen = [], set()
    for index, item in enumerate(plan):
        raw = (work / 'calls' / (item['name'] + '.raw.json')).read_bytes()
        if sha(raw) != canonical[index]['rawSha256'] or item['states'] != canonical[index]['states']:
            raise ValueError('Native packet differs from its pinned compiled source')
        data = json.loads(raw)
        ids = [frame['id'] for frame in data['frames']]
        if ids != item['states'] or any(s not in sources or s in seen for s in ids):
            raise ValueError('Unexpected, missing or duplicated packet state')
        if data['snapshot'] != manifest['sourceSnapshotSha256'] or data['capture'] != manifest['captureSha256']:
            raise ValueError('Mixed source or conversion snapshot')
        for frame in data['frames']:
            source = json.loads((work / 'scenes' / sources[frame['id']]['file']).read_text(encoding='utf-8'))
            if (frame['width'], frame['height']) != (source['scene']['width'], source['scene']['height']):
                raise ValueError('Scene dimensions differ from packet')
        seen.update(ids)
        used = {node[6] for node in data['nodes'] if node[0] == 3}
        data.pop('replaceId', None)
        # Large native baselines occur once in the queue, avoiding 94 repeated copies in memory.
        def identity_rows(rows):
            return rows if index == 0 else [{k: v for k, v in row.items() if k != 'nativeInventory'} for row in rows]

        data.update(format='etaye-windows-native-v2', packetIndex=index, rawPacketSha256=sha(raw),
                    totalPackets=len(plan), totalStates=len(sources), fileKey=state['fileKey'],
                    pageId=state['pageId'], expectedExisting=identity_rows(state['completedSections']),
                    cleanupCandidates=identity_rows(state['cleanupCandidates']),
                    imageData={key: assets[key] for key in sorted(used)})
        packets.append((item['name'] + '.json', data))
    if seen != set(sources):
        raise ValueError('Incomplete native state set')
    return packets, state, manifest


def export(work, artifact, fonts, output):
    work, artifact, fonts, output = [p.resolve() for p in (work, artifact, fonts, output)]
    if output.is_relative_to(ROOT):
        raise ValueError('Generated kits must remain outside the repository')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    status = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).splitlines()
    if status:
        raise ValueError('Commit the complete source tree before exporting the final kit')
    packets, state, scenes = prepare_packets(work)
    snapshot = json.loads((artifact / 'review/corrected-snapshot.json').read_text(encoding='utf-8'))
    actual = {p.relative_to(artifact).as_posix(): sha(p.read_bytes())
              for p in (artifact / 'site').rglob('*') if p.is_file()}
    if actual != {row['path']: row['sha256'] for row in snapshot['files']}:
        raise ValueError('Corrected HTML artifact no longer matches its complete snapshot')
    if snapshot['snapshotSha256'] != scenes['sourceSnapshotSha256']:
        raise ValueError('HTML and native scenes use different source snapshots')
    font_sources = json.loads((ROOT / 'design/figma-local-import/font-sources.json').read_text())
    for row in font_sources:
        if sha((fonts / row['file']).read_bytes()) != row['sha256']:
            raise ValueError('Font or license differs from its pinned source')
    output.mkdir(parents=True, exist_ok=True)
    folder = Path(tempfile.mkdtemp(prefix='.etaye-windows-incomplete-', dir=output))
    package_name = 'Etaye-Figma-Windows-20261117-' + folder.name.rsplit('-', 1)[-1]
    origins = {}

    def put(name, data, origin):
        target = folder / name
        if target.exists() or target.is_symlink() or not target.resolve().is_relative_to(folder):
            raise ValueError('Unsafe or duplicate kit path')
        if b'\x00' not in data and SECRET.search(data):
            raise ValueError('Potential credential blocked in ' + name)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        origins[name] = origin

    def copy(source, name):
        if source.is_symlink() or not source.is_file():
            raise ValueError('Expected an ordinary source file')
        put(name, source.read_bytes(), 'source copy')

    for name, data in packets:
        put('batches/' + name, (json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n').encode(), 'verified native packet')
    for source in sorted((ROOT / 'design/figma-windows-import').iterdir()):
        if source.is_file():
            copy(source, 'plugin/' + source.name)
    for source in sorted(artifact.rglob('*')):
        if source.is_file() and not source.is_symlink() and '__pycache__' not in source.parts and source.suffix != '.pyc':
            copy(source, 'maquettes/' + source.relative_to(artifact).as_posix())
    for source in sorted(fonts.rglob('*')):
        if source.is_file():
            copy(source, 'windows-fonts/' + source.relative_to(fonts).as_posix())
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    for name in filter(None, tracked):
        source = ROOT / name
        if source.is_file() and not source.is_symlink():
            copy(source, 'repository-source/' + name)
    for name in ['MANIFEST.json', 'PLAN.json', 'EXISTING-FIGMA-STATE.json', 'export.py', 'compile.py', 'codec.py', 'render.js', 'capture-enhanced.js']:
        copy(work / name, 'preparation-evidence/' + name)
    # Selected data and code only: no upload configs, tokens, virtual environments or signed URLs.
    for source in sorted((work / 'scenes').glob('*.json')):
        copy(source, 'preparation-evidence/scenes/' + source.name)
    for source in sorted((work / 'results').glob('*.json')):
        copy(source, 'preparation-evidence/results/' + source.name)
    copy(ROOT / 'docs/design/windows-handoff/START-HERE.md', 'START-HERE.md')
    copy(ROOT / 'docs/design/windows-handoff/RESUME-PROMPT.txt', 'RESUME-PROMPT.txt')
    copy(ROOT / 'docs/design/windows-handoff/VERIFY-KIT.ps1', 'VERIFY-KIT.ps1')
    put('EXISTING-FIGMA-STATE.json', (json.dumps(state, ensure_ascii=False, indent=2) + '\n').encode(), 'actual dated native read-back')
    copied_site = {p.relative_to(folder / 'maquettes').as_posix(): sha(p.read_bytes())
                   for p in (folder / 'maquettes/site').rglob('*') if p.is_file()}
    if copied_site != actual:
        raise ValueError('Copied site differs from the verified frozen snapshot')
    for row in font_sources:
        if sha((folder / 'windows-fonts' / row['file']).read_bytes()) != row['sha256']:
            raise ValueError('Copied font/license differs from its pinned source')
    manifest = {'kind': 'offline-windows-figma-handoff', 'generatedAt': datetime.now(timezone.utc).isoformat(),
                'sourceCommit': head, 'sourceTreeDirty': bool(status), 'sourceTreeStatus': status,
                'states': 456, 'families': 76, 'packets': 94, 'viewport': 'desktop1440',
                'alreadyImportedStates': 8, 'nativeWindowsExecutionVerified': False,
                'sourceSnapshotSha256': scenes['sourceSnapshotSha256'], 'captureSha256': scenes['captureSha256'],
                'files': {name: {'sha256': sha((folder / name).read_bytes()),
                                 'bytes': (folder / name).stat().st_size, 'origin': origin}
                          for name, origin in sorted(origins.items())}}
    (folder / 'MANIFEST.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    members = set(origins) | {'MANIFEST.json'}
    archive = output / ('.incomplete-' + package_name + '.zip')
    with zipfile.ZipFile(archive, 'x', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for member in sorted(members):
            z.write(folder / member, package_name + '/' + member)
    with zipfile.ZipFile(archive) as z:
        if z.testzip() or set(z.namelist()) != {package_name + '/' + member for member in members}:
            raise ValueError('ZIP membership or CRC mismatch')
        for member in members:
            if z.read(package_name + '/' + member) != (folder / member).read_bytes():
                raise ValueError('ZIP differs from delivered payload: ' + member)
    completed = output / package_name
    completed_archive = output / (package_name + '.zip')
    sums = output / (package_name + '-SHA256SUMS.txt')
    if any(p.exists() for p in (completed, completed_archive, sums)):
        raise FileExistsError('Refusing to replace an existing completed kit')
    folder = folder.rename(completed)
    archive = archive.rename(completed_archive)
    sums.write_text(sha(archive.read_bytes()) + '  ' + archive.name + '\n' +
                    sha((folder / 'MANIFEST.json').read_bytes()) + '  ' + folder.name + '/MANIFEST.json\n', encoding='utf-8')
    return {'folder': str(folder), 'archive': str(archive), 'checksums': str(sums),
            'files': len(members), 'archiveBytes': archive.stat().st_size,
            'archiveSha256': sha(archive.read_bytes()), 'sourceCommit': head,
            'verified': True, 'windowsNativeExecution': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ['work', 'artifact', 'fonts', 'output']:
        parser.add_argument('--' + flag, type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(export(args.work, args.artifact, args.fonts, args.output), indent=2))


if __name__ == '__main__':
    main()

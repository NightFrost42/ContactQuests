#!/usr/bin/env python3
"""Exercise release validation in isolated fixtures; never upload an artifact."""
import importlib.util
import io
import json
import os
from pathlib import Path
import runpy
import tempfile
import unittest
import urllib.error
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('prepare_release', ROOT / '.github/scripts/prepare-release.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class ReleaseValidation(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.previous = Path.cwd()
        os.chdir(self.temp.name)
        self.addCleanup(os.chdir, self.previous)

    def prepare(self, version='2.1.6-1.21.1', tag=None, draft=False, prerelease=False, body='Verified release notes',
                event_name=''):
        Path('gradle.properties').write_text(f'mod_version={version}\nminecraft_version=1.21.1\n')
        release = {'draft': draft, 'prerelease': prerelease, 'body': body,
                   'name': 'Test release', 'html_url': 'https://github.com/NightFrost42/ContactQuests/releases/test'}
        env = {'RELEASE_TAG': tag or 'v' + version, 'GITHUB_REPOSITORY': 'NightFrost42/ContactQuests',
               'GH_TOKEN': 'fixture-token-not-a-credential', 'GITHUB_OUTPUT': 'outputs.txt',
               'GITHUB_EVENT_NAME': event_name}
        with patch.dict(os.environ, env), patch.object(prepare.urllib.request, 'urlopen',
                return_value=io.BytesIO(json.dumps(release).encode())):
            prepare.main()

    def prepare_404(self, version='2.1.6-1.21.1', tag=None, changelog=None, event_name='push'):
        Path('gradle.properties').write_text(f'mod_version={version}\nminecraft_version=1.21.1\n')
        if changelog is not None:
            Path('CHANGELOG.md').write_text(changelog, encoding='utf-8')
        env = {'RELEASE_TAG': tag or version, 'GITHUB_REPOSITORY': 'NightFrost42/ContactQuests',
               'GH_TOKEN': 'fixture-token-not-a-credential', 'GITHUB_OUTPUT': 'outputs.txt',
               'GITHUB_EVENT_NAME': event_name}
        not_found = urllib.error.HTTPError('https://api.github.com/test', 404, 'Not Found', None, None)
        with patch.dict(os.environ, env), patch.object(prepare.urllib.request, 'urlopen', side_effect=not_found):
            prepare.main()

    def test_final_release_uses_notes_and_exact_version(self):
        self.prepare()
        data = json.loads(Path('release-dist/release.json').read_text())
        self.assertEqual(data['version'], '2.1.6-1.21.1')
        self.assertEqual(data['versionType'], 'release')
        self.assertTrue(data['releaseExists'])
        self.assertEqual(Path('release-dist/CHANGELOG.md').read_text(), 'Verified release notes\n')

    def test_tag_push_reads_changelog_when_github_release_missing(self):
        changelog = (
            "# Changelog\n\n"
            "## [2.1.6-1.21.1] - 2026-11-19\n\n"
            "### Added\n- Postcard choice reward\n\n"
            "## [2.1.5-1.21.1] - 2026-10-01\n\n- Old notes\n"
        )
        for tag in ('2.1.6-1.21.1', 'v2.1.6-1.21.1'):
            with self.subTest(tag=tag):
                Path('outputs.txt').unlink(missing_ok=True)
                self.prepare_404(version='2.1.6-1.21.1', tag=tag, changelog=changelog, event_name='push')
                data = json.loads(Path('release-dist/release.json').read_text())
                self.assertEqual(data['tag'], tag)
                self.assertEqual(data['version'], '2.1.6-1.21.1')
                self.assertEqual(data['versionType'], 'release')
                self.assertFalse(data['prerelease'])
                self.assertFalse(data['releaseExists'])
                self.assertEqual(Path('release-dist/CHANGELOG.md').read_text(encoding='utf-8'),
                                 "### Added\n- Postcard choice reward\n")
                outputs = Path('outputs.txt').read_text(encoding='utf-8')
                self.assertIn(f"tag={tag}\n", outputs)
                self.assertIn("prerelease=false\n", outputs)
                self.assertIn("release-exists=false\n", outputs)

    def test_tag_push_prerelease_flags_and_changelog_fallback_on_empty_release(self):
        changelog = "## [2.1.6-beta.1-1.21.1]\n- Beta build\n\n## [2.1.6-1.21.1]\n- Release notes\n"
        self.prepare_404(version='2.1.6-beta.1-1.21.1', tag='2.1.6-beta.1-1.21.1', changelog=changelog, event_name='push')
        data = json.loads(Path('release-dist/release.json').read_text())
        self.assertEqual(data['versionType'], 'beta')
        self.assertTrue(data['prerelease'])

        Path('CHANGELOG.md').write_text(changelog, encoding='utf-8')
        self.prepare(version='2.1.6-1.21.1', tag='2.1.6-1.21.1', body='  ', event_name='release')
        self.assertEqual(Path('release-dist/CHANGELOG.md').read_text(encoding='utf-8'), '- Release notes\n')

    def test_alpha_and_beta_are_prereleases(self):
        for suffix, expected in [('alpha.1', 'alpha'), ('beta.1', 'beta'), ('rc.1', 'beta')]:
            self.prepare(version='2.1.6-' + suffix + '-1.21.1', prerelease=True)
            self.assertEqual(json.loads(Path('release-dist/release.json').read_text())['versionType'], expected)

    def test_rejects_invalid_release_inputs(self):
        cases = [dict(tag='v2.1.7-1.21.1'), dict(version='2.1.6-dev-1.21.1'),
                 dict(draft=True), dict(body='  '), dict(version='2.1.6-rc.1-1.21.1', prerelease=False)]
        for case in cases:
            with self.subTest(case=case), self.assertRaises(ValueError):
                self.prepare(**case)
        with self.subTest(case='404_missing_changelog'), self.assertRaises(ValueError):
            self.prepare_404(changelog='## [2.1.5-1.21.1]\n- Older\n', event_name='push')
        with self.subTest(case='404_on_release_event'), self.assertRaises(ValueError):
            self.prepare_404(changelog='## [2.1.6-1.21.1]\n- Present\n', event_name='release')

    def jar(self, version='2.1.6-1.21.1', mod_id='contactquests', test_mod=False):
        Path('release-dist').mkdir(exist_ok=True)
        Path('release-dist/release.json').write_text(json.dumps({'version': '2.1.6-1.21.1'}))
        Path('build/libs').mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile('build/libs/contactquests-2.1.6-1.21.1.jar', 'w') as jar:
            jar.writestr('META-INF/neoforge.mods.toml', f'[[mods]]\nmodId="{mod_id}"\nversion="{version}"\n')
            if test_mod:
                jar.writestr('com/creeperyang/contactquests/test/Test.class', b'not production')

    def test_production_jar_checksum_and_copy(self):
        self.jar()
        runpy.run_path(str(ROOT / '.github/scripts/verify-release-jar.py'))
        self.assertTrue(Path('release-dist/contactquests-2.1.6-1.21.1.jar').is_file())
        self.assertRegex(Path('release-dist/SHA256SUMS').read_text(), r'^[0-9a-f]{64}  contactquests-2\.1\.6-1\.21\.1.jar\n$')

    def test_rejects_wrong_version_mod_or_test_classes(self):
        for case in [dict(version='2.1.5-1.21.1'), dict(mod_id='other'), dict(test_mod=True), dict(version='${mod_version}')]:
            self.jar(**case)
            with self.subTest(case=case), self.assertRaises(AssertionError):
                runpy.run_path(str(ROOT / '.github/scripts/verify-release-jar.py'))


if __name__ == '__main__':
    unittest.main()

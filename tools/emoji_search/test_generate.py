import tempfile
import unittest
from pathlib import Path
from generate import key, read_cldr_metadata


class CldrMetadataTest(unittest.TestCase):
    def parse(self, base, derived):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / 'ja.xml', Path(directory) / 'ja-derived.xml']
            for path, rows in zip(paths, [base, derived]):
                path.write_text('<ldml><annotations>' + rows + '</annotations></ldml>')
            return read_cldr_metadata(paths)

    def test_skin_variants_cannot_replace_base_tts_or_add_skin_only_keywords(self):
        metadata = self.parse(
            '<annotation cp="🙏" type="tts">祈り</annotation><annotation cp="🙏">手 | 祈り</annotation>',
            '<annotation cp="🙏🏿" type="tts">祈り: 濃い肌色</annotation>'
            '<annotation cp="🙏🏿">手 | 祈り | 濃い肌色</annotation>'
            '<annotation cp="🙏🏻" type="tts">祈り: 薄い肌色</annotation>')
        self.assertEqual('祈り', metadata[key('🙏')]['name'])
        self.assertEqual({'手', '祈り'}, metadata[key('🙏')]['keywords'])
        self.assertEqual(1, len(metadata))

    def test_presentation_aliases_keep_the_base_name_and_gender_zwj_stays_distinct(self):
        metadata = self.parse(
            '<annotation cp="🙇‍♂" type="tts">おじぎする男</annotation>'
            '<annotation cp="🙇‍♀" type="tts">おじぎする女</annotation>',
            '<annotation cp="🙇‍♂️" type="tts">おじぎする男</annotation>'
            '<annotation cp="🙇🏽‍♂️" type="tts">おじぎする男: 中間の肌色</annotation>')
        self.assertEqual(2, len(metadata))
        self.assertEqual('おじぎする男', metadata[key('🙇‍♂️')]['name'])
        self.assertEqual('おじぎする女', metadata[key('🙇‍♀️')]['name'])


if __name__ == '__main__':
    unittest.main()

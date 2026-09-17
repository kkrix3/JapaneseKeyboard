import unittest
from prepare_build import version_code
from verify_apk import method_resource_path


class BuildChecksTest(unittest.TestCase):
    def test_common_sequence_increases_for_old_and_new_feature_sources(self):
        self.assertEqual(1000000001, version_code(1))
        self.assertLess(version_code(42), version_code(43))
        self.assertEqual(2100000000, version_code(1100000000))

    def test_sequence_rejects_zero_negative_and_overflow(self):
        for number in [0, -1, 1100000001]:
            with self.subTest(number=number), self.assertRaises(AssertionError):
                version_code(number)

    def test_optimized_xml_is_resolved_from_compiled_table(self):
        table='''Package name=com.example id=7f
  type xml id=19 entryCount=3
    resource 0x7f190001 xml/backup_rules
      () (file) res/AB.xml type=XML
    resource 0x7f190002 xml/method
      () (file) res/CD.xml type=XML
    resource 0x7f190003 xml/preferences
      () (file) res/EF.xml type=XML
'''
        self.assertEqual(('0x7f190002','res/CD.xml'),method_resource_path(table))
        self.assertEqual(('0x7f190002','res/xml/method.xml'),
                         method_resource_path(table.replace('res/CD.xml','res/xml/method.xml')))

    def test_missing_or_conflicting_method_resource_fails_closed(self):
        for table in ['', 'resource 0x7f000001 xml/method\n () (file) res/a.xml type=XML\n (ja) (file) res/b.xml type=XML']:
            with self.subTest(table=table), self.assertRaises(AssertionError):
                method_resource_path(table)


if __name__=='__main__': unittest.main()

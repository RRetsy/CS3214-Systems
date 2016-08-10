declare variable $doc_name as xs:string external;
for $root in doc('libx2_meta')/metadata/roots[@document=$doc_name]/id return $root


declare namespace libx='http://libx.org/xml/libx2';
declare namespace atom='http://www.w3.org/2005/Atom';
declare variable $id as xs:string external;
declare variable $doc_name as xs:string external;
doc($doc_name)/atom:feed/atom:entry[atom:id=$id]


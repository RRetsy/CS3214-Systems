(: find and return atom:entry's of libapps and packages who refer to a given id :)
declare namespace libx='http://libx.org/xml/libx2';
declare namespace atom='http://www.w3.org/2005/Atom';
declare variable $id as xs:string external;
declare variable $doc_name as xs:string external;
let $feed := doc($doc_name)/atom:feed
return $feed/atom:entry[./libx:entry[@src=$id]]

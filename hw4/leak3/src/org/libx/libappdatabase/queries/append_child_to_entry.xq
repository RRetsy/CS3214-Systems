declare namespace libx='http://libx.org/xml/libx2';
declare namespace atom='http://www.w3.org/2005/Atom';
declare variable $child_id  as xs:string external;
declare variable $parent_id as xs:string external;
declare variable $doc_name as xs:string external;
let $feed := doc($doc_name)/atom:feed
let $parent_entry := $feed/atom:entry[atom:id=$parent_id]
return insert node <libx:entry src='{$child_id}' xmlns:libx='http://libx.org/xml/libx2' />
into $parent_entry//(libx:module|libx:libapp|libx:package)


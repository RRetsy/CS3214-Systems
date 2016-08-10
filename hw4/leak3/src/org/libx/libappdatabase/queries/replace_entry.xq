declare namespace libx='http://libx.org/xml/libx2';
declare namespace atom='http://www.w3.org/2005/Atom';

declare variable $entry as xs:anyAtomicType external;
declare variable $id    as xs:string external;
declare variable $doc_name as xs:string external;

let $feed := doc($doc_name)/atom:feed
let $old := $feed/atom:entry[atom:id=$id]
return replace node $old with $entry


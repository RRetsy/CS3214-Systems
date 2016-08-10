declare namespace libx="http://libx.org/xml/libx2";
declare namespace atom="http://www.w3.org/2005/Atom";
declare variable $doc_name as xs:string external;
declare variable $target_url as xs:string external;
declare variable $id as xs:string external;
let $entry := doc($doc_name)/atom:feed/atom:entry[atom:id = $id]
return 
<entry xmlns="http://www.w3.org/2005/Atom">
    <id>{ $target_url }</id>
    { $entry/*[local-name() != 'id'] }
</entry>

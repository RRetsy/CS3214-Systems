declare namespace libx='http://libx.org/xml/libx2';
declare namespace atom='http://www.w3.org/2005/Atom';
declare variable $doc_name as xs:string external;
declare variable $entry_type as xs:string external;
let $metadata := doc('libx2_meta')/metadata
let $curid := $metadata/curid
return replace value of node $curid with data($curid) + 1,

let $metadata := doc('libx2_meta')/metadata
let $feed := doc($doc_name)/atom:feed
let $newid := data($metadata/curid) + 1
let $title := $entry_type
let $author_name := 'LibX Team'
let $author_uri := 'http://libx.org'
let $author_email := 'libx.org@gmail.com'
let $module_body := 'module body'
let $libx_node := 
  if(($entry_type = 'package') or ($entry_type = 'libapp')) then
    element {fn:concat("libx:", $entry_type)} {()}
  else if($entry_type = 'module') then
    <libx:module>
      <libx:body>{$module_body}</libx:body>
    </libx:module>
  else ()
return insert node
  <entry xmlns:libx="http://libx.org/xml/libx2" xmlns="http://www.w3.org/2005/Atom">
    <id>{$newid}</id>
    <title>New {$title}</title>
    <updated>{fn:current-dateTime()}</updated>
    <author>
      <name>{$author_name}</name>
      <uri>{$author_uri}</uri>
      <email>{$author_email}</email>
    </author>
    <content type="html">Content created by LibX Libapp Builder</content> 
    {$libx_node}
  </entry>
into $feed

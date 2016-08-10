declare namespace libx='http://libx.org/xml/libx2';
declare namespace atom='http://www.w3.org/2005/Atom';
declare variable $doc_name as xs:string external;
declare variable $entry as xs:anyAtomicType external;
let $feed := doc($doc_name)/atom:feed
let $metadata := doc('libx2_meta')/metadata
let $curid := $metadata/curid
return replace value of node $curid with data($curid) + 1,

let $feed := doc($doc_name)/atom:feed
let $metadata := doc('libx2_meta')/metadata
let $newid := data($metadata/curid) + 1
let $author_name := 'LibX Team'
let $author_uri := 'http://libx.org'
let $author_email := 'libx.org@gmail.com'
return insert node
  <entry xmlns:libx="http://libx.org/xml/libx2" xmlns="http://www.w3.org/2005/Atom">
    <id>{$newid}</id>
    <title>Copy of { $entry//atom:title/text() }</title>
    <updated>{fn:current-dateTime()}</updated>
    <author>
      <name>{$author_name}</name>
      <uri>{$author_uri}</uri>
      <email>{$author_email}</email>
    </author>
    <content type="html">Content created by LibX Libapp Builder</content> 
    { $entry/libx:module | $entry/libx:package | $entry/libx:libapp }
  </entry>
into $feed

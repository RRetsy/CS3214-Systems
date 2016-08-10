declare namespace libx='http://libx.org/xml/libx2';
declare namespace atom='http://www.w3.org/2005/Atom';
declare variable $doc_name as xs:string external;

declare function local:getTitle($feed, $id) as xs:string {
  data($feed/atom:entry[atom:id=$id]/atom:title)
};
declare function local:getType($feed, $id) as xs:string {
  let $libx_node := name($feed/atom:entry[atom:id=$id]/(libx:module|libx:libapp|libx:package))
  return
    if      ($libx_node = 'libx:package') then 'package'
    else if ($libx_node = 'libx:libapp')  then 'libapp'
    else if ($libx_node = 'libx:module')  then 'module'
    else 'unknown'
};
declare function local:buildSubTree($id) {
  let $feed := doc($doc_name)/atom:feed
  let $parent := $feed/atom:entry[atom:id=$id]
  let $children := data($parent//libx:entry/@src)
  return
  if (fn:count($children) = 0) then ()
  else
    for $child_id in $children
      return <node id='{$child_id}' type='{local:getType($feed, $child_id)}' title='{local:getTitle($feed, $child_id)}'>{
        local:buildSubTree($child_id)
      }</node>
};

let $feed := doc($doc_name)/atom:feed
let $metadata := doc('libx2_meta')/metadata
let $roots := $metadata/roots[@document=$doc_name]/id
return <root>{
for $r in $roots
  return <node id='{$r}' type='{local:getType($feed, $r)}' title='{local:getTitle($feed, $r)}'>{ local:buildSubTree($r) }</node>
}</root>


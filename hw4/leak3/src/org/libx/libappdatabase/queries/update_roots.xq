declare namespace libx='http://libx.org/xml/libx2';
declare namespace atom='http://www.w3.org/2005/Atom';
declare variable $doc_name as xs:string external;
declare function local:complement($A, $B) as xs:string()* {
 let $A := data($A)
  let $B := data($B)
    for $a in $A return
      if (not(exists(index-of($B, $a)))) then $a
      else ()
};
let $root_doc := doc('libx2_meta')/metadata/roots[@document=$doc_name]
return delete node $root_doc/id,

let $root_doc := doc('libx2_meta')/metadata/roots[@document=$doc_name]
let $feed_doc  := doc($doc_name)/atom:feed
let $entries   := $feed_doc/atom:entry
let $children  := distinct-values($feed_doc/atom:entry//libx:entry/@src)
let $new_roots := local:complement($entries/atom:id, $children)
for $r in $new_roots return
  insert node <id>{data($r)}</id> into $root_doc


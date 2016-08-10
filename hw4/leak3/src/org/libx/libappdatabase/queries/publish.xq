declare namespace libx="http://libx.org/xml/libx2";
declare namespace atom="http://www.w3.org/2005/Atom";
declare variable $doc_name as xs:string external;
declare variable $target_url as xs:string external;
let $feed := doc($doc_name)/atom:feed
return 
<feed xmlns="http://www.w3.org/2005/Atom">
    <id>{ $target_url }</id>
    <updated>{ fn:current-dateTime() }</updated>
    { $feed/*[local-name() != 'id' and local-name() != 'entry'] }
    {
        for $entry in $feed/atom:entry
        return <entry>
            <id>{ $target_url }/{ $entry/atom:id/text() }</id>
            { $entry/*[local-name() != 'id'] }
        </entry>
    }
</feed>

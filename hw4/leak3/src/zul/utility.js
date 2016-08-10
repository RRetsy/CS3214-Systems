/*
 * Utility routines for edition builder.
 */
/*
 * If enter is pressed, send 'onChange' event to textbox widget.
 * This will send changed value to server and trigger onChange event there.
 *
 * If submitButton != null, also send a 'click' event to submit button
 * control - this will trigger onClick for the button there.
 *
 * Doing so allows us to combine onChange/onChanging in a textbox
 * that is not associated with a text button.  Used in the auto-detect box
 */
function fireSubmitOnEnter(e, changedTextbox, submitButton) 
{
    if (e.keyCode == 13) {
        // see http://docs.zkoss.org/wiki/Zk.Widget
        changedTextbox.fire('onChange', { value: changedTextbox.$n().value });

        if (submitButton) {
            // Native clicks contain: {"pageX":395,"pageY":181,"which":1,"x":38,"y":12}
            // only 'which' (the button, presumably), is required.
            submitButton.fire('onClick', { which: 1 });
        }
    }
}

// http://sourceforge.net/tracker/index.php?func=detail&aid=1683475&group_id=152762&atid=785194
/*
 * FIXME: No longer used in ZK 5.0.1 
 */
function Boot_progressbox(id, msg, x, y) {
  //construct a progress box
  var html = '<div id="' + id + '" style="position: absolute; ' 
        + 'padding: 1em; background-color: #E0E0E0; color: #240F8B; font-size: 14px; '
        + 'left: ' + (x+5) + 'px; top: ' + (y+5) + 'px;">' 
        + msg + ' please wait.</div>';
  document.body.insertAdjacentHTML("afterbegin", html);
  return $e(id);
}

/*
 * When a html link is clicked, find the corresponding server side hidden
 * zk 'a' component by its id and trigger onClick event on it.
 */
function fireClick(link) {
    zk.Widget.newInstance('box').$f(link, true).fire('onClick', {which:1})
}

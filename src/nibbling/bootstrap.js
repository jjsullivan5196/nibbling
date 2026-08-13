function morph_body(s) {
    /* Replace contents of body with html in string `s`.
     */
    Idiomorph.morph(document.body, s, { morphStyle: "innerHTML" });
}

function on_input(e) {
    /* Raise a command to the host from input event `e`.
     * Generated command is of the form `["app/name", value]`.
     */
    const t = e.target;
    const cmd = JSON.stringify([t.name, t.value]);

    _host.receive(cmd);
}

// Listen for all input changes.
addEventListener('input', on_input, { capture: true });

// First render.
_host.render();


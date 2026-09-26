document.onreadystatechange = function(){
    // editor.on seems to require newer codemirror version
    // var pending;
    // editor.on("change", function() {
    //      clearTimeout(pending);
    //      pending = setTimeout(update, 400);
    //  });
    function looksLike(code) {
        var firstline = code.split('\n')[0];
        if(firstline.indexOf('#!') == 0){
            var shellpattern = /bin\/[\w]{0,2}sh/;
            var shellpatternwin = /[\w]{0,2}sh\.exe/;
            if(firstline.indexOf('python') > -1){
                return "python";
                // perl requires stapler-adjunct-codemirror > 1.3
            }else if(firstline.indexOf('perl') > -1){
                return "perl";
            }else if(shellpattern.exec(firstline) || shellpatternwin.exec(firstline)){
                return "shell";
            }
        }
    }
    function update() {
        editor.setOption("mode", looksLike(editor.getValue()));
    }
    update();
};

/**
 * HTML5-based upload script. Uses HTML5's File API as well as XMLHttpRequests's native file
 * support. Tested in Chrome only, since it's just me who uses the backend.
 *
 * Copyright (c) 2012 by Dean Harding (dean@codeka.com.au)
 */


(function($) {

  $.extend({
    uploadify: {

      // default settings, for when you don't specify anything yourself.
      defaults: {
        buttonText: "Browse...",
        sendAsForm: false,
        onProgress: function(file, percent) {},
        onUploadComplete: function(file, success, response) {},

        uploadUrl: ""
      },

      init: function (dom, options) {
        var $this = this;
        this.options = $.extend({}, this.defaults, options);

        // we assume they've given us a <input type="file"> (if you don't, things don't
        // work) so we'll need to hide that one and add our button instead.
        this.input = dom;
        dom.attr("multiple", "multiple");
        dom.css("position", "absolute").css("visibility", "collapse");
        dom.change(function(file) { $this._onFileSelected(file); });

        this.button = $("<input type=\"button\" value=\""+this.options.buttonText+"\">");
        this.button.insertAfter(dom);

        // clicking the button is like selecting a file...
        this.button.click(function() { dom.click(); });
      },

      // when you select a file, we need to initiate the upload...
      _onFileSelected: function(evnt) {
        evnt.preventDefault();
        for (var i = 0, file; file = evnt.target.files[i]; i++) {
          this._startUpload(file);
        }
      },

      // starts upload the given file
      _startUpload: function(file) {
        var xhr = new XMLHttpRequest();

        var $this = this;
        xhr.upload.addEventListener("progress", function(evnt) {
          if (evnt.lengthComputable) {
            var percent = Math.round((evnt.loaded * 100) / evnt.total);
            $this.options.onProgress(file, percent);
          }
        }, false);

        xhr.onreadystatechange = function() {
          if (xhr.readyState == 4) {
            resp = JSON.parse(xhr.responseText);
            if (resp.upload_url) {
              // if they've given us a new upload URL we need to update ourselves so that
              // we can handle a new upload
              console.log("New upload_url: "+resp.upload_url);
              $this.options.uploadUrl = resp.upload_url;
            }

            $this.options.onUploadComplete(file, xhr.status, resp);
          }
        };

        xhr.open("POST", $this.options.uploadUrl, true);
        xhr.setRequestHeader("X-File-Name", file.name);
        xhr.setRequestHeader("X-File-Type", file.type);

        if ($this.options.sendAsForm) {
          var form = new FormData();
          form.append("file", file);
          xhr.send(form);
        } else if (xhr.sendAsBinary) {
          xhr.overrideMimeType("application/binary");

          var reader = new FileReader();
          reader.onload = function(evnt) {
            xhr.sendAsBinary(evnt.target.result);
          };
          reader.readAsBinaryString(file);
        } else {
          xhr.setRequestHeader("Content-Type", "application/binary");
          xhr.send(file);
        }
      }
    }
  });

  $.fn.uploadify = function(options) {
    return $.uploadify.init(this, options);
  }

})(jQuery);

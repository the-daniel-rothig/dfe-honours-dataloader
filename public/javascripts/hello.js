window.onload = function() {
    document.getElementById("upload-files").addEventListener("click", function(e) {
        this.disabled = true;
        this.value = "Processing files...";
    });
    document.getElementById("upload-files2").addEventListener("click", function(e) {
        this.disabled = true;
        this.value = "Processing files...";
    });
}

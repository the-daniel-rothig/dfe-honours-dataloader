window.onload = function() {
    var inputs = document.getElementsByTagName("input");
    for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].attributes.hasOwnProperty("type") && inputs[i].attributes.getNamedItem("type").value === "submit") {
            inputs[i].addEventListener("click", function(e) {
                  this.disabled = true;
                  this.value = "Processing...";
              });
        }
    }
}

var displayJSONLD = function(url) {

  var addField = function(target, field, value) {
    tr = $("<tr/>");
    tr.append("<td class='field'>"+field+":</td>");
    tr.append("<td>"+value+"</td>"); 
    target.append(tr); 
  }
    
  $.get(url+".jsonld", function(data) {

    container = $("#display");

    data.forEach(function(g){
      if (g["@id"].endsWith("#provenance")){
        container.append("<h3>Provenance</h3>");
        table = $("<table></table>");
        container.append(table);
        g["@graph"].forEach(function(obj){
          if(obj["http://xmlns.com/foaf/0.1/name"]) {
            addField(table, "Creator", obj["http://xmlns.com/foaf/0.1/name"][0]["@value"]);         
          }

          if(obj["http://www.w3.org/ns/prov#generatedAtTime"]) {
            addField(table, "Assertion created at time", obj["http://www.w3.org/ns/prov#generatedAtTime"][0]["@value"]);
          }
          
        });
      }

      else if (g["@id"].endsWith("#pubinfo")){
        container.append("<h3>Publication information</h3>");
        table = $("<table></table>");
        container.append(table);
        g["@graph"].forEach(function(obj){
          if(obj["@id"].endsWith("#emaresp")) {
            addField(table, "Nanopublication created by", obj["http://xmlns.com/foaf/0.1/name"][0]["@value"]);         
          }

          if(obj["http://www.w3.org/ns/prov#generatedAtTime"]) {
            addField(table, "Nanopublication generated at time", obj["http://www.w3.org/ns/prov#generatedAtTime"][0]["@value"]);
          }
          
        });
      }

      else if (g["@id"].endsWith("#assertion")){
        container.append("<h3>Assertion (as <a href='http://www.w3.org/TR/annotation-model/'>Open Annotation</a>)</h3>");
        table = $("<table></table>");

        mot_row = $("<tr>");
        mot_td = $("<td colspan='2'>");
        mot_td.append("<h4>Motivation</h4>")
        mot_row.append(mot_td);
        table.append(mot_row);

        main_row = $("<tr>")
        left_td = $("<td>");
        right_td = $("<td>");
        main_row.append(left_td);
        main_row.append(right_td);
        table.append(main_row);
        body = $("<table>");
        target = $("<table>");
        left_td.append("<h4>Body (tags)</h4>");
        left_td.append(body);
        right_td.append("<h4>Target (<a href='https://github.com/umd-mith/ema'>EMA expression</a>)</h4>");
        right_td.append(target);
        container.append(table);
        g["@graph"].forEach(function(obj){

          if (obj["@id"].endsWith("#observation")) {
            if(obj["http://www.w3.org/ns/oa#motivatedBy"]) {
              ms = [];
              obj["http://www.w3.org/ns/oa#motivatedBy"].forEach(function(m){
                ms.push(m["@id"].split("#")[1])
              });
              mot_td.append(ms.join(", "));
            }
          }         

          else if (obj["@id"].endsWith("#target")) {
            emaExpr = obj["http://www.w3.org/ns/oa#hasSource"][0]["@id"];
            split = emaExpr.split("/");
            root = split.slice(0, -4); 
            uri = "/<span class='uri'>"+split.slice(-4,-3)+"</span>";
            mm = "/<span class='mm'>"+split.slice(-3,-2)+"</span>";
            ss = "/<span class='ss'>"+split.slice(-2,-1)+"</span>";
            bb = "/<span class='bb'>"+split.slice(-1)+"</span>";
            target.append("<a href='"+emaExpr+"' class='emaExpr'>"+root.join("/")+uri+mm+ss+bb+"</a>");
            verovioDiv = $("<div style='margin-top:1em;'>\
              <strong>Approximate rendering</strong> (with <a href='http://verovio.org'>Verovio</a>)\
              <div id='verovioBox'></div></div>");
            target.append(verovioDiv);
            verovioDiv.append("")

            var vrvToolkit = new verovio.toolkit();
            scale = 50;
            options = JSON.stringify({
              pageWidth: $("#verovioBox").width() * 100 / scale,
              ignoreLayout: 1,
              adjustPageHeight: 1,
              border: 50,
              scale: scale
            });
            vrvToolkit.setOptions(options);

            // Load and display MEI
            $.get(emaExpr, function(mei){
              vrvToolkit.loadData( mei + "\n", "" );
              for (num = i = 1; i <= vrvToolkit.getPageCount(); num = ++i) {
                svg = vrvToolkit.renderPage(num);
                $("#verovioBox").append(svg);
              }
            }, "text");
          }
          else {
            // Tags here
            label = obj["http://www.w3.org/2000/01/rdf-schema#label"][0]["@value"];
            label = label.replace(/_/g, " ");
            label = label.charAt(0).toUpperCase() + label.slice(1);
            value = obj["http://www.w3.org/1999/02/22-rdf-syntax-ns#value"][0]["@value"];
            addField(body, label, value); 
          }
          
        });
      }

    });
  }, "json");
}
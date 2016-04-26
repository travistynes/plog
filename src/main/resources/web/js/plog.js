// Global namespace object.
var P = {};
P.page = 1;

// Called on window load.
P.load = function() {
    P.setup();
    
    // Perform initial log request.
    P.getLogs();
};

P.setup = function() {
    // If the hostname is not "", it is running on a server. Clear the sample/test rows.
    if(location.hostname !== "") {
        $("#logTable tbody tr").remove();
    }
    
    // Show the log table. It's hidden so the above test rows aren't visible briefly on page load.
    $("#logTable").show();
    
    // Setup log level radio button change handler.
    $("#options input[type='radio'][name='log_level']").change(function() {
        // A log level radio button was clicked.
        P.optionChanged();
    });
    
    // Setup logger search box change handler.
    $("#loggerSearchBox").keyup(function() {
        // Clear a pending log refresh, and reschedule. This prevents refresh on rapid key presses.
        clearTimeout(P.loggerSearchBoxChangeTimeoutID);
        P.loggerSearchBoxChangeTimeoutID = setTimeout(P.optionChanged, 500);
    });
    
    // Sort order selection.
    $("#sortOrder").change(function() {
        P.optionChanged();
    });
    
    // Setup realtime (tail) checkbox change handler.
    $("#tail").change(function() {
        var checked = $(this).is(":checked");
        if(!checked) {
            // Tail was disabled. Cancel pending log request.
            clearTimeout(P.tailTimeoutID);
            
            // Hide realtime status bar.
            $("#realtimeStatusbar").hide();
        } else {
            // Tail was enabled.
            P.getLogs();
            
            // Show realtime status bar.
            $("#realtimeStatusbar").show();
        }
    });
    
    // Setup timestamp search field.
    $("#ts").val(moment(new Date()).format("YYYY-MM-DD HH:mm:ss.SSS")); // Now.
    $("#ts").keyup(function(e) {
        if(e.keyCode === 13) {
            // User pressed enter.
            P.optionChanged();
        }
    });
    
    // Range selection.
    $("#range").change(function() {
        P.optionChanged();
    });
    
    // Setup browse left/right buttons.
    $("#browseLeft").click(function() {
        if(P.page > 1) {
            // Decrease page number.
            P.page--;
            
            // Query the database.
            P.getLogs();
        }
    });
    
    $("#browseRight").click(function() {
        // Increase the page number.
        P.page++;
        
        // Query the database.
        P.getLogs();
    });
};

// Called when an option is changed.
P.optionChanged = function() {
    // Get logs based on new selection unless realtime is enabled.
    var tail = $("#tail").is(":checked");
    if(tail) { return; }
    
    // Reset the page number.
    P.page = 1;
    
    P.getLogs();
}

// Requests logs based on user selections.
P.getLogs = function() {
    // Check if tail is enabled.
    if($("#tail").is(":checked")) {
        // Setup search values for realtime query.
        P.page = 1;
        $("#sortOrder").val("DESC");
        $("#range").val("-1_hours");
        $("#ts").val(moment(new Date()).format("YYYY-MM-DD HH:mm:ss.SSS")); // Now.
    }
    
    // Get selections.
    var level = $("#options input[type='radio'][name='log_level']:checked").val();
    var logger = $("#loggerSearchBox").val().trim() || "all"; // If the value is "", send "all".
    var order = $("#sortOrder").val();
    
    // Set from, to timestamps.
    var range = $("#range").val().split("_");
    var amount = parseInt(range[0], 10);
    var a = moment($("#ts").val());
    var b = a.clone().add(amount, range[1]);
    var from = a;
    var to = b;
    if(a > b) {
        from = b;
        to = a;
    }
    
    // Set footer text.
    $("#footer").removeClass("error_message");
    $("#footer").text("Wait.");
    
    $.ajax({
        method: "GET",
        url: "logs",
        timeout: 15000, // millis
        data: {
            level: level,
            logger: logger,
            from: encodeURI(from.format("YYYY-MM-DD HH:mm:ss.SSS")),
            to: encodeURI(to.format("YYYY-MM-DD HH:mm:ss.SSS")),
            order: order,
            page: P.page
        }
    }).done(function(data) {
        // Success
        var messages = JSON.parse(data);
        
        if($("#tail").is(":checked")) {
            // Messages are in asc order, but we want to view them desc.
            //messages.reverse();
        }
        
        // Display the logs.
        P.showLogs(messages);
        
        // Display the current page in the footer.
        $("#footer").text("Page: " + P.page);
    }).fail(function(jqXHR, textStatus, errorThrown) {
        // Fail. Display error message in the footer.
        $("#footer").addClass("error_message");
        $("#footer").text("Error requesting logs.");
    }).always(function() {
        // Complete (after done or fail). Schedule new request if tail is enabled.
        var tail = $("#tail").is(":checked");
        
        if(tail) {
            // Tail is enabled. Schedule a log refresh.
            clearTimeout(P.tailTimeoutID); // To be safe, clear any pending refresh.
            P.tailTimeoutID = setTimeout(P.getLogs, 1000);
        }
    });
};

P.showLogs = function(messages) {
    var t = $("#logTable tbody");
    
    // Remove existing rows.
    t.find("tr").remove();
    
    // Add rows.
    for(var a = 0; a < messages.length; a++) {
        var m = messages[a];
        var row = "<tr><td class='ts'>" + m.ts + "</td><td class='" + m.level + "'>" + m.level + "</td><td>" + m.logger + "</td><td class='message'>" + m.message + "</td></tr>";
        
        t.append(row);
    }
    
    // Setup timestamp column hover handlers.
    $("#logTable tbody tr td.ts").hover(function() {
        // Hover over.
        $(this).addClass("ts_hover");
    },
    function() {
        // Hover off.
        $(this).removeClass("ts_hover");
    });
    // Setup timestamp column click handlers.
    $("#logTable tbody tr td.ts").click(function() {
        // Set the timestamp text field to the value of the selected timestamp.
        var ts = $(this).text();
        $("#ts").val(ts);
        
        // Submit.
        P.optionChanged();
    });
};

// Load event handler fired when all DOM objects, images, and scripts are loaded.
window.onload = P.load;

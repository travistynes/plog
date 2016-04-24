// Global namespace object.
var P = {};
P.direction = "left";

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
    
    // Setup realtime (tail) checkbox change handler.
    $("#tail").change(function() {
        var checked = $(this).is(":checked");
        if(!checked) {
            // Tail was disabled. Cancel pending log request.
            clearTimeout(P.tailTimeoutID);
            
            // Hide realtime status bar.
            $("#realtimeStatusbar").hide();
        } else {
            // Tail was enabled. Request logs.
            P.getLogs();
            
            // Show realtime status bar.
            $("#realtimeStatusbar").show();
        }
    });
    
    // Setup timestamp search field.
    $("#ts").val(moment(new Date()).format("YYYY-MM-DD HH:mm:ss.SSS")); // Now.
    $("#ts").keyup(function(e) {
        if(e.key === "Enter") {
            // User pressed enter. Query logs for entered timestamp, desc order.
            P.direction = "left";
            P.getLogs();
        }
    });
    
    // Setup browse left/right buttons.
    $("#browseLeft").click(function() {
        if($("#logTable tbody tr").length > 0) {
            // Set timestamp field to the last ts in the current result set.
            var ts = $("#logTable tbody tr td.ts").last().text();
            $("#ts").val(ts);
        }
        
        // Set the search direction
        P.direction = "left";
        
        // Query the database.
        P.getLogs();
    });
    
    $("#browseRight").click(function() {
        if($("#logTable tbody tr").length > 0) {
            // Set timestamp field to the first ts in the current result set.
            var ts = $("#logTable tbody tr td.ts").first().text();
            $("#ts").val(ts);
        }
        
        // Set the search direction
        P.direction = "right";
        
        // Query the database.
        P.getLogs();
    });
};

// Called when an option is changed.
P.optionChanged = function() {
    // Get logs based on new selection unless realtime is enabled.
    var tail = $("#tail").is(":checked");
    if(tail) { return; }
    
    P.getLogs();
}

// Requests logs based on user selections.
P.getLogs = function() {
    // Check if tail is enabled.
    if($("#tail").is(":checked")) {
        // Set search values.
        $("#ts").val(moment(new Date()).format("YYYY-MM-DD HH:mm:ss.SSS")); // Now.
        P.direction = "left";
    }
    
    // Get selections.
    var level = $("#options input[type='radio'][name='log_level']:checked").val();
    var logger = $("#loggerSearchBox").val().trim() || "all"; // If the value is "", send "all".
    var ts = $("#ts").val();
    var direction = P.direction;
    
    $.ajax({
        method: "GET",
        url: "logs",
        timeout: 15000, // millis
        data: {
            level: level,
            logger: logger,
            ts: encodeURI(ts),
            direction: direction
        }
    }).done(function(data) {
        // Success
        var messages = JSON.parse(data);
        
        if(direction === "right") {
            // Messages are in asc order, but we want to view them desc.
            messages.reverse();
        }
        
        P.showLogs(messages);
    }).fail(function(jqXHR, textStatus, errorThrown) {
        // Fail
        
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
};

// Load event handler fired when all DOM objects, images, and scripts are loaded.
window.onload = P.load;

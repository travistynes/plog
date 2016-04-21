// Global namespace object.
var P = {};

// Called on window load.
P.load = function() {
    P.setup();
    
    // Perform initial log request.
    P.getLogs();
};

P.setup = function() {
    // If the hostname is not "", it is running on a server. Clear the sample/test rows.
    if(!location.hostname === "") {
        var t = $("#logTable tbody tr").remove();
    }
    
    // Show the log table. It's hidden so the above test rows aren't visible briefly on page load.
    $("#logTable").show();
    
    // Setup log level radio button change handler.
    $("#options input[type='radio'][name='log_level']").change(function() {
        // A log level radio button was clicked.
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
            // Tail was enabled. Request logs.
            P.getLogs();
            
            // Show realtime status bar.
            $("#realtimeStatusbar").show();
        }
    });
    
    // Setup datepickers.
    $("#fromDate").val($.datepicker.formatDate("yy-mm-dd", new Date())); // Default to today.
    $("#fromDate").datepicker({
        dateFormat: "yy-mm-dd",
        defaultDate: new Date(),
        autoClose: true,
        onSelect: function(dt, picker) {
            P.optionChanged();
        }
    });
    
    $("#toDate").val($.datepicker.formatDate("yy-mm-dd", new Date(new Date().getTime() + (1000 * 60 * 60 * 24)))); // Default to tomorrow.
    $("#toDate").datepicker({
        dateFormat: "yy-mm-dd",
        defaultDate: new Date(),
        autoClose: true,
        onSelect: function(dt, picker) {
            P.optionChanged();
        }
    });
    
    // Setup logger search box change handler.
    $("#loggerSearchBox").keyup(function() {
        // Clear a pending log refresh, and reschedule. This prevents refresh on rapid key presses.
        clearTimeout(P.loggerSearchBoxChangeTimeoutID);
        P.loggerSearchBoxChangeTimeoutID = setTimeout(P.optionChanged, 500);
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
    // Get selections.
    var level = $("#options input[type='radio'][name='log_level']:checked").val();
    var from = $("#fromDate").val();
    var to = $("#toDate").val();
    var logger = $("#loggerSearchBox").val().trim() || "all"; // If the value is "", send "all".
    
    $.ajax({
        method: "GET",
        url: "logs",
        timeout: 15000, // millis
        data: {
            level: level,
            from: from,
            to: to,
            logger: logger
        }
    }).done(function(data) {
        // Success
        var messages = JSON.parse(data);
        P.showLogs(messages);
    }).fail(function(jqXHR, textStatus, errorThrown) {
        // Fail
        
    }).always(function() {
        // Complete (after done or fail). Schedule new request if tail is enabled.
        var tail = $("#tail").is(":checked");
        
        if(tail) {
            // Tail is enabled. Schedule a log refresh.
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

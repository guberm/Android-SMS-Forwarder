/**
 * Google Apps Script for Android SMS Forwarder
 * 
 * Instructions:
 * 1. Go to script.google.com
 * 2. Create a new project.
 * 3. Paste this code.
 * 4. Click 'Deploy' > 'New Deployment'.
 * 5. Select 'Web App'.
 * 6. Execute as: 'Me'.
 * 7. Who has access: 'Anyone'.
 * 8. Copy the Web App URL and paste it into the Android App.
 */

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    
    // Use device timestamp if provided, otherwise use server time
    var timestamp = data.timestamp ? new Date(data.timestamp) : new Date();
    var sender = data.sender || "Unknown";
    var message = data.message || "No content";
    
    // 1. Log to Google Sheet
    var sheet = getOrCreateLogSheet();
    sheet.appendRow([timestamp, sender, message]);
    
    // 2. Send Email
    var userEmail = Session.getActiveUser().getEmail();
    var subject = "New SMS from " + sender;
    var body = "You received a new SMS:\n\n" +
               "From: " + sender + "\n" +
               "Time: " + timestamp.toLocaleString('ru-RU') + "\n\n" +
               "Message:\n" + message;
               
    GmailApp.sendEmail(userEmail, subject, body);
    
    return ContentService.createTextOutput(JSON.stringify({"status": "success"}))
      .setMimeType(ContentService.MimeType.JSON);
      
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({"status": "error", "message": err.message}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function getOrCreateLogSheet() {
  var fileName = "SMS Forwarder Logs";
  var files = DriveApp.getFilesByName(fileName);
  var ss;
  
  if (files.hasNext()) {
    ss = SpreadsheetApp.open(files.next());
  } else {
    ss = SpreadsheetApp.create(fileName);
    ss.getSheets()[0].appendRow(["Timestamp", "Sender", "Message"]);
  }
  
  return ss.getSheets()[0];
}

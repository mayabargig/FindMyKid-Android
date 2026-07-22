const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.onHelpRequestCreated = functions.database
  .ref("/helpRequests/{childUid}/{requestId}")
  .onCreate(async (snap, context) => {
    const { childUid, requestId } = context.params;
    const help = snap.val() || {};

    const note = (help.note || "").toString();
    const ts = help.timestamp || Date.now();
    const lat = help.latitude;
    const lng = help.longitude;

    // 1) parentUid
    const parentUidSnap = await admin
      .database()
      .ref(`/children/${childUid}/parentUid`)
      .get();

    const parentUid = parentUidSnap.val();
    if (!parentUid) return null;

    // 2) notificationsEnabled
    const enabledSnap = await admin
      .database()
      .ref(`/parents/${parentUid}/notificationsEnabled`)
      .get();

    const enabled = !!enabledSnap.val();
    if (!enabled) return null;

    // 3) tokens
    const tokensSnap = await admin
      .database()
      .ref(`/parents/${parentUid}/fcmTokens`)
      .get();

    const tokensObj = tokensSnap.val() || {};
    const tokens = Object.keys(tokensObj).filter((t) => tokensObj[t] === true);
    if (tokens.length === 0) return null;

    // ✅ DATA-ONLY (כדי ש-onMessageReceived יעבוד גם ברקע)
    const multicastMessage = {
      tokens,
      data: {
        type: "HELP_REQUEST",
        title: "🚨 SOS מהילד",
        body: note && note.length > 0 ? note : "הילד ביקש עזרה עכשיו",
        childUid: String(childUid),
        requestId: String(requestId),
        lat: String(lat ?? ""),
        lng: String(lng ?? ""),
        timestamp: String(ts),
      },
      android: {
        priority: "high",
      },
    };

    const res = await admin.messaging().sendEachForMulticast(multicastMessage);

    // ניקוי טוקנים לא תקינים
    const updates = {};
    res.responses.forEach((r, idx) => {
      if (!r.success) {
        const code = r.error?.code || "";
        if (code.includes("registration-token-not-registered")) {
          updates[`/parents/${parentUid}/fcmTokens/${tokens[idx]}`] = null;
        }
      }
    });

    if (Object.keys(updates).length > 0) {
      await admin.database().ref().update(updates);
    }

    return null;
  });

package com.example.qrazyqrsrus;

import static android.graphics.ImageDecoder.createSource;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.checkerframework.checker.units.qual.A;
import org.checkerframework.checker.units.qual.C;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a utility class that does all the Firebase related actions.
 */
public class FirebaseDB {

    public interface UniqueCheckCallBack {
        void onResult(boolean isUnique);
    }

    public interface UniqueCheckInCallBack {
        void onResult(boolean isUnique, CheckIn checkIn);
    }

    //- we change the callback interface for looking for a matching qr code to have an OnNoResult function that handles the case that no matching qr code exists for either promo or checkin
    //then in qr scanhandler we call findEventWithQR in OnNoResult for the promo qr search
    //then in qr scanhandler we throw error in onNoResult after both qr searches
    public interface MatchingQRCallBack {
        void onResult(Event matchingEvent);

        void onNoResult();
    }

    public interface GetStringCallBack{
        void onResult(String string);
    }

    public interface GetBitmapCallBack{
        void onResult(Bitmap bitmap);
    }

    public interface GetAttendeeCallBack {
        void onResult(Attendee attendee);
    }
    public interface GetAllEventsCallBack {
        void onResult(ArrayList<Event> events);
    }

    // Define a callback interface to handle the result
    public interface GetEventCallback {
        Event onSuccess(Event event);

        void onFailure(String errorMessage);
    }


    public interface OnFinishedCallback{
        void onFinished();
    }

    public interface AttemptLoginCallback {
        void onResult();

        void onNoResult();
    }

    public interface StringArrayCallback {
        void onResult(ArrayList<String> array);
    }

    public interface GetTokenCallback{
        void onResult(String token);
    }


    public interface GetMapMarkersCallback {
        void onResult(ArrayList<CheckIn> checks, ArrayList<String> names);
    }

    //singleton
    private static FirebaseDB instance = null;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseMessaging messaging;
    private CollectionReference usersCollection;
    private CollectionReference eventsCollection;
    private CollectionReference checkInsCollection;
    private CollectionReference adminLoginsCollection;

    public CollectionReference getUsersCollection() {
        return usersCollection;
    }

    public void setUsersCollection(CollectionReference usersCollection) {
        this.usersCollection = usersCollection;
    }

    public CollectionReference getEventsCollection() {
        return eventsCollection;
    }

    public void setEventsCollection(CollectionReference eventsCollection) {
        this.eventsCollection = eventsCollection;
    }

    public CollectionReference getCheckInsCollection() {
        return checkInsCollection;
    }

    public void setCheckInsCollection(CollectionReference checkInsCollection) {
        this.checkInsCollection = checkInsCollection;
    }

    public CollectionReference getAdminLoginsCollection() {
        return adminLoginsCollection;
    }

    public void setAdminLoginsCollection(CollectionReference adminLoginsCollection) {
        this.adminLoginsCollection = adminLoginsCollection;
    }

    public FirebaseFirestore getDb() {
        return db;
    }

    public void setDb(FirebaseFirestore db) {
        this.db = db;
    }

    public FirebaseStorage getStorage() {
        return storage;
    }

    public void setStorage(FirebaseStorage storage) {
        this.storage = storage;
    }

    public FirebaseMessaging getMessaging() {
        return messaging;
    }

    public void setMessaging(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    final String usersTAG = "Users";
    final String eventsTAG = "Events";
    final String imagesTAG = "Images";

    final String checkInsTag = "CheckIns";



    public static FirebaseDB getInstance(){
        if (instance == null){
            FirebaseFirestore firestoreInstance = FirebaseFirestore.getInstance();
            instance = new FirebaseDB(firestoreInstance, FirebaseStorage.getInstance(), FirebaseMessaging.getInstance(), firestoreInstance.collection("Users"), firestoreInstance.collection("Events"),
                    firestoreInstance.collection("CheckIns"), firestoreInstance.collection("Logins"));
        }
        return instance;
    }

    /**
     * This method is used to get a test instance of FirebaseDB that can use Mock Firebase Firestore/Storage/Messaging instances, to perform tests without hitting the actual database.
     * @param firestoreInstance
     * @param firebaseStorageInstance
     * @param firebaseMessagingInstance
     * @return
     */
    public static FirebaseDB getInstance(FirebaseFirestore firestoreInstance, FirebaseStorage firebaseStorageInstance, FirebaseMessaging firebaseMessagingInstance){
        return new FirebaseDB(firestoreInstance, firebaseStorageInstance, firebaseMessagingInstance, firestoreInstance.collection("Users"), firestoreInstance.collection("Events"),
                firestoreInstance.collection("CheckIns"), firestoreInstance.collection("Logins"));
    }

    //dependency injection doesn't work, because db is a static variable
    //consider refactoring FirebaseDB into a singleton with dependency injection
    //we don't want to mock FirebaseDB, we want to mock FirebaseFirestore.getInstance()
    private FirebaseDB(FirebaseFirestore firestoreInstance, FirebaseStorage storageInstance, FirebaseMessaging messagingInstance, CollectionReference usersCollection, CollectionReference eventsCollection,
                       CollectionReference checkInsCollection, CollectionReference adminLoginsCollection){
        this.db = firestoreInstance;
        this.storage = storageInstance;
        this.messaging = messagingInstance;
        this.usersCollection = usersCollection;
        this.eventsCollection = eventsCollection;
        this.checkInsCollection = checkInsCollection;
        this.adminLoginsCollection = adminLoginsCollection;
    }

    // Change String to Attendee class when someone implements it.

    /**
     * Adds a document that represents a user in the database
     *
     * @param user The user we want to add
     */
    public void addUser(Attendee user) {
        usersCollection
                .add(user)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
//                        Log.d(usersTAG, "User document snapshot written with ID:" + documentReference.getId());
                        user.setDocumentId(documentReference.getId());
                        updateUser(user);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
//                        Log.w(usersTAG, "Error while adding user document", e);
                    }
                });
    }

    /**
     * Logs the user in depending on their unique identifier. Will create a new user if this is
     * the first time they have opened the app.
     *
     * @param userId The unique identifier of the user that has opened the app
     */

    public void loginUser(String userId, GetAttendeeCallBack callBack) {
        usersCollection
                .whereEqualTo("id", userId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (task.getResult() == null || task.getResult().isEmpty()) {
                                Attendee attendee = new Attendee(userId);
                                addUser(attendee);
                                callBack.onResult(attendee);
                            } else {
                                for (DocumentSnapshot documentSnapshot: task.getResult()) {
                                    Attendee attendee = documentSnapshot.toObject(Attendee.class);
                                    attendee.setDocumentId(documentSnapshot.getId());
                                    callBack.onResult(attendee);
                                }
                            }
                        } else {
                            Log.e("MainActivity", "Error trying to login");
                        }
                    }
                });
    }

    /**
     * Adds a document that represents an event in the database
     *
     * @param event The event we want to add
     */
    public void addEvent(Event event) {
        eventsCollection
                .add(event)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(eventsTAG, "event document snapshot written with ID:" + documentReference.getId());
                        event.setDocumentId(documentReference.getId());
                        updateEvent(event);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(eventsTAG, "Error while adding event document", e);
                    }
                });
    }

    /**
     * Updates the document that represents the user in the database.
     *
     * @param user The user that needs their document updated.
     */
    public void updateUser(Attendee user) {
        usersCollection
                .document(user.getDocumentId())
                .update("name", user.getName(),
                        "email", user.getEmail(),
                        "geolocationOn", user.getGeolocationOn(),
                        "profilePicturePath", user.getProfilePicturePath(), "documentId", user.getDocumentId())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
//                        Log.d(usersTAG, "User document updated successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
//                        Log.w(usersTAG, "Error while updating user document", e);
                    }
                });
    }

    /**
     * Updates the document that represents the event in the database.
     *
     * @param event The event that needs its document updated.
     */
    public void updateEvent(Event event) {
        eventsCollection
                .document(event.getDocumentId())
                .update("announcements", event.getAnnouncements(),
                        "checkIns", event.getCheckIns(), "signUps",
                        event.getSignUps(), "posterPath", event.getPosterPath(),
                        "qrCode", event.getQrCode(), "documentId", event.getDocumentId(),
                        "organizerToken", event.getOrganizerToken())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(eventsTAG, "Event document updated successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(eventsTAG, "Error while updating event document", e);
                    }
                });
    }

    /**
     * Retrieves an image from the database
     *
     * @param file     The file in the local system
     * @param pathName This is the path of the image. In the form {folder}/{image} where
     *                 folder is either profiles, posters, qrcodes, and image is just the name of
     *                 the image
     */
    public void uploadImage(Uri file, String pathName) {
        StorageReference storageRef = storage.getReference();
        StorageReference storageReference = storageRef.child(pathName + ".jpg");

        storageReference.putFile(file)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d(imagesTAG, "Successful image upload");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(imagesTAG, "Failed to upload image");
                    }
                });

    }

    /**
     * Retrieves an image from the database
     *
     * @param user This is the user we want to retrieve their profile picture
     * @param callBack This callBack will be used to get back bitmap
     */
    public void retrieveImage(Attendee user, GetBitmapCallBack callBack) {
        //ArrayList<Bitmap> localBitMap = new ArrayList<Bitmap>();
        try {
            StorageReference storageRef = storage.getReference(user.getProfilePicturePath() + ".jpg");
            File localFile = File.createTempFile(user.getProfilePicturePath().split("/")[1], "jpg");
            storageRef.getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    callBack.onResult(BitmapFactory.decodeFile(localFile.getAbsolutePath()));
                    Log.d(imagesTAG, "Successfully retrieved image");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    Log.w(imagesTAG, "Failed to retrieve image: " + exception);
                }
            });
        } catch (IOException exception) {
            Log.e(imagesTAG, "Error trying to retrieve image: " + exception);
        }
        //return localBitMap.get(0);
    }

    /**
     * Retrieves an image from the database
     *
     * @param event This is the event we're trying to get its poster.
     * @param callBack This callBack will be used to get back bitmap
     */
    public void retrieveImage(Event event, GetBitmapCallBack callBack) {
        try {
            StorageReference storageRef = storage.getReference(event.getPosterPath() + ".jpg");
            File localFile = File.createTempFile(event.getPosterPath().split("/")[1], "jpg");
            storageRef.getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    callBack.onResult(BitmapFactory.decodeFile(localFile.getAbsolutePath()));
                    Log.d(imagesTAG, "Successfully retrieved image");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    Log.w(imagesTAG, "Failed to retrieve image: " + exception);
                }
            });
        } catch (IOException exception) {
            Log.e(imagesTAG, "Error trying to retrieve image: " + exception);
        }
    }

    public void retrieveImage(String path, GetBitmapCallBack callBack) {
        try {
            StorageReference storageRef = storage.getReference(path + ".jpg");
            File localFile = File.createTempFile(path.split("/")[1], "jpg");
            storageRef.getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    callBack.onResult(BitmapFactory.decodeFile(localFile.getAbsolutePath()));
                    Log.d(imagesTAG, "Successfully retrieved image");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    Log.w(imagesTAG, "Failed to retrieve image: " + exception);
                }
            });
        } catch (IOException exception) {
            Log.e(imagesTAG, "Error trying to retrieve image: " + exception);
        }
    }

    /**
     * Deletes an image from the database
     *
     * @param pathName the pathname where we can find the file in the database storage
     */
    public void deleteImage(String pathName){
        StorageReference storageRef = storage.getReference();
        StorageReference storageReference = storageRef.child(pathName + ".jpg");

        storageReference.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                //TODO: handle bad attempts to delete
            }
        });
    }

    /**
     * Retrieves all events in the events collection
     *
     * @param eventList         The list we're going to hold the events in.
     * @param eventArrayAdapter The ArrayAdapter of eventList.
     */
    public void getAllEvents(ArrayList<Event> eventList, ArrayAdapter<Event> eventArrayAdapter) {
        eventsCollection
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            Log.d(eventsTAG, "Retrieved all events");
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String id = document.getId();
                                String name = (String) document.getData().get("name");
                                String organizerId = (String) document.getData().get("organizerId");
                                String details = (String) document.getData().get("details");
                                String location = (String) document.getData().get("location");
                                String startDate = (String) document.getData().get("startDate");
                                String endDate = (String) document.getData().get("endDate");
                                Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");
                                String posterPath = (String) document.getData().get("posterPath");
                                String qrCode = (String) document.getData().get("qrCode");
                                String qrCodePromo = (String) document.getData().get("qrCodePromo");
                                String organizerToken = (String) document.getData().get("organizerToken");
                                Long maxAttendeesLong = (Long) document.getData().get("maxAttendees");
                                Integer maxAttendees = maxAttendeesLong != null ? Math.toIntExact(maxAttendeesLong) : null;
                                ArrayList<String> announcements = (ArrayList<String>) document.getData().get("announcements");
                                if (announcements == null){
                                    announcements = new ArrayList<String>();
                                }
                                ArrayList<String> signUps = (ArrayList<String>) document.getData().get("signUps");
                                if (signUps == null){
                                    signUps = new ArrayList<String>();
                                }
                                ArrayList<String> checkIns = (ArrayList<String>) document.getData().get("checkIns");
                                if (checkIns == null){
                                    checkIns = new ArrayList<String>();
                                }

                                Event event = new Event(id, name, organizerId, details, location, startDate, endDate, geolocationOn, posterPath, qrCode, qrCodePromo, organizerToken, announcements, signUps, checkIns, maxAttendees);
                                eventList.add(event);
                            }
                            eventArrayAdapter.notifyDataSetChanged();
                        } else {
                            Log.d(eventsTAG, "Error getting documents: ", task.getException());
                        }
                    }
                });

    }
    public void getAllEvents(GetAllEventsCallBack callBack) {
        ArrayList<Event> eventList = new ArrayList<>();
        eventsCollection
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            Log.d(eventsTAG, "Retrieved all events");
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String id = document.getId();
                                String name = (String) document.getData().get("name");
                                String organizerId = (String) document.getData().get("organizerId");
                                String details = (String) document.getData().get("details");
                                String location = (String) document.getData().get("location");
                                String startDate = (String) document.getData().get("startDate");
                                String endDate = (String) document.getData().get("endDate");
                                Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");
                                String posterPath = (String) document.getData().get("posterPath");
                                String qrCode = (String) document.getData().get("qrCode");
                                String qrCodePromo = (String) document.getData().get("qrCodePromo");
                                String organizerToken = (String) document.getData().get("organizerToken");
                                Long maxAttendeesLong = (Long) document.getData().get("maxAttendees");
                                Integer maxAttendees = maxAttendeesLong != null ? Math.toIntExact(maxAttendeesLong) : null;
                                ArrayList<String> announcements = (ArrayList<String>) document.getData().get("announcements");
                                if (announcements == null){
                                    announcements = new ArrayList<String>();
                                }
                                ArrayList<String> signUps = (ArrayList<String>) document.getData().get("signUps");
                                if (signUps == null){
                                    signUps = new ArrayList<String>();
                                }
                                ArrayList<String> checkIns = (ArrayList<String>) document.getData().get("checkIns");
                                if (checkIns == null){
                                    checkIns = new ArrayList<String>();
                                }

                                Event event = new Event(id, name, organizerId, details, location, startDate, endDate, geolocationOn, posterPath, qrCode, qrCodePromo, organizerToken, announcements, signUps, checkIns, maxAttendees);
                                eventList.add(event);
                            }
                            callBack.onResult(eventList);
                        } else {
                            Log.d(eventsTAG, "Error getting documents: ", task.getException());
                        }
                    }
                });

    }

    /**
     * Retrieves all users in the users collection
     *
     * @param attendeeList         The list we're going to hold the users in.
     * @param attendeeArrayAdapter The ArrayAdapter of attendeeList.
     */
    public void getAllUsers(ArrayList<Attendee> attendeeList, ArrayAdapter<Attendee> attendeeArrayAdapter) {
        usersCollection
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            Log.d(usersTAG, "Retrieved all users");
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String documentId = document.getId();
                                String id = (String) document.getData().get("id");
                                String name = (String) document.getData().get("name");
                                String email = (String) document.getData().get("email");
                                String profilePicturePath = (String) document.getData().get("profilePicturePath");
                                Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");


                                Attendee attendee = new Attendee(id, documentId, name, email, profilePicturePath, geolocationOn);
                                attendeeList.add(attendee);
                                attendeeArrayAdapter.notifyDataSetChanged();
                            }
                        } else {
                            Log.d(usersTAG, "Error getting documents: ", task.getException());
                        }
                    }
                });

    }

    /**
     * Retrieves all users in the users collection, but does not store them into an Array Adapter.
     *
     * @param attendeeList         The list we're going to hold the users in.
     * @param callback The OnFinishedCallback that we will invoke once firebase is done it's operation
     */
    public void getAllUsers(ArrayList<Attendee> attendeeList, OnFinishedCallback callback) {
        usersCollection
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            Log.d(usersTAG, "Retrieved all users");
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String documentId = document.getId();
                                String id = (String) document.getData().get("id");
                                String name = (String) document.getData().get("name");
                                String email = (String) document.getData().get("email");
                                String profilePicturePath = (String) document.getData().get("profilePicturePath");
                                Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");


                                Attendee attendee = new Attendee(id, documentId, name, email, profilePicturePath, geolocationOn);
                                attendeeList.add(attendee);
                            }
                            callback.onFinished();
                        } else {
                            Log.d(usersTAG, "Error getting documents: ", task.getException());
                        }
                    }
                });

    }

    /**
     * Retrieves all the events an user has signed up to
     *
     * @param user the user who as signed up to events
     * @param eventArrayList the list you want to add the events to
     */
    public void getAttendeeSignedUpEvents(Attendee user, ArrayList<Event> eventArrayList, HomeSignedUpListAdapter adapter) {
        eventsCollection
                .whereArrayContains("signUps", user.getDocumentId())
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                            Event event = documentSnapshot.toObject(Event.class);
                            if (event.getAnnouncements() == null){
                                event.setAnnouncements(new ArrayList<String>());
                            }
                            if (event.getSignUps() == null){
                                event.setSignUps(new ArrayList<String>());
                            }
                            if (event.getCheckIns() == null){
                                event.setCheckIns(new ArrayList<String>());
                            }
                            event.setDocumentId(documentSnapshot.getId());
                            eventArrayList.add(event);
                        }
                        adapter.notifyDataSetChanged();
                        Log.d(eventsTAG, "Events successfully retrieved");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(eventsTAG, "Failed to retrieve events of user");
                    }
                });
    }

    /**
     * Retrieves all the events an user has checked in to
     *
     * @param user the user who as checked in to events
     * @param eventList the list passed in to get the events
     * @param adapter the adapter used to update the ListView
     */
    public void getEventsCheckedIn(Attendee user, ArrayList<Event> eventList, HomeCheckedInListAdapter adapter) {
        ArrayList<String> myCheckIns = new ArrayList<>();
        checkInsCollection
                .whereEqualTo("attendeeDocId", user.getDocumentId())
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        for (DocumentSnapshot documentSnapshot : task.getResult()) {
                            myCheckIns.add((String) documentSnapshot.get("eventDocId"));
                        }
                        if (!myCheckIns.isEmpty()) {
                            eventsCollection.whereIn("documentId", myCheckIns).get()
                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                            if (task.isSuccessful()) {
                                                for (DocumentSnapshot document : task.getResult()) {
                                                    String id = document.getId();
                                                    String name = (String) document.getData().get("name");
                                                    String organizerId = (String) document.getData().get("organizerId");
                                                    String details = (String) document.getData().get("details");
                                                    String location = (String) document.getData().get("location");
                                                    String startDate = (String) document.getData().get("startDate");
                                                    String endDate = (String) document.getData().get("endDate");
                                                    Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");
                                                    String posterPath = (String) document.getData().get("posterPath");
                                                    String qrCode = (String) document.getData().get("qrCode");
                                                    String qrCodePromo = (String) document.getData().get("qrCodePromo");
                                                    String organizerToken = (String) document.getData().get("organizerToken");
                                                    Long maxAttendeesLong = (Long) document.getData().get("maxAttendees");
                                                    Integer maxAttendees = maxAttendeesLong != null ? Math.toIntExact(maxAttendeesLong) : null;
                                                    ArrayList<String> announcements = (ArrayList<String>) document.getData().get("announcements");
                                                    if (announcements == null) {
                                                        announcements = new ArrayList<String>();
                                                    }
                                                    ArrayList<String> signUps = (ArrayList<String>) document.getData().get("signUps");
                                                    if (signUps == null) {
                                                        signUps = new ArrayList<String>();
                                                    }
                                                    ArrayList<String> checkIns = (ArrayList<String>) document.getData().get("checkIns");
                                                    if (checkIns == null) {
                                                        checkIns = new ArrayList<String>();
                                                    }

                                                    Event event = new Event(id, name, organizerId, details, location, startDate, endDate, geolocationOn, posterPath, qrCode, qrCodePromo, organizerToken, announcements, signUps, checkIns, maxAttendees);
                                                    eventList.add(event);
                                                }
                                                adapter.notifyDataSetChanged();
                                            } else {
                                                Log.d("Firestore", "Error getting documents: ", task.getException());
                                            }
                                        }
                                    });
                        }
                    }
                });

    }

    /**
     * Retrieves all the attendees that have signed up an event
     *
     * @param event the event we're getting the attendees
     * @param attendeeArrayList the list passed in to get the attendees
     */
    public void getEventSignedUp(Event event, ArrayList<Attendee> attendeeArrayList) {
        for (String signUps : event.getSignUps()) {
            usersCollection
                    .document(signUps)
                    .get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            Attendee attendee = documentSnapshot.toObject(Attendee.class);
                            attendee.setDocumentId(documentSnapshot.getId());
                            attendeeArrayList.add(attendee);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w(usersTAG, "Failed " + e);
                        }
                    });
        }
    }

    /**
     * Retrieves all the attendees that have checked in an event
     *
     * @param event the event we're getting the attendees
     * @param attendeeArrayList the list passed in to get the events
     */
    public void getEventCheckedIn(Event event, ArrayList<Attendee> attendeeArrayList,  ArrayAdapter<Attendee> attendeeArrayAdapter) {
        checkInsCollection
                .whereEqualTo("eventDocId", event.getDocumentId())
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        for (DocumentSnapshot documentSnapshot : task.getResult()) {
                            usersCollection
                                    .document((String) Objects.requireNonNull(documentSnapshot.get("attendeeDocId")))
                                    .get()
                                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                        @Override
                                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                                            Attendee attendee = documentSnapshot.toObject(Attendee.class);
                                            attendee.setDocumentId(documentSnapshot.getId());
                                            attendeeArrayList.add(attendee);
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.w(usersTAG, "Failed " + e);
                                        }
                                    });

                        }
                        attendeeArrayAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(usersTAG, "Failed " + e);
                    }
                });

    }

    /** This returns the name of the user with that document id
     * @param userDocumentId This the document id of the user
     * @param callBack This callBack will be used to get back the name of user
     */
    public void getUserName(String userDocumentId, GetStringCallBack callBack) {
        usersCollection.document(userDocumentId).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        Log.d(usersTAG, "Success");
                        callBack.onResult((String) documentSnapshot.get("name"));
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(usersTAG, "Failed "+e);
                    }
                });
    }

    /**
     * \
     * Check whether or not the content of a QR code is already in use by an event created in the app.
     *
     * @param qrContent a string that represents the content field of the QR code we are checking
     * @param mode an integer: 0 if we are checking promotional qr codes, 1 if we are checking checkin qr codes
     * @param callBack a class that implements the UniqueCheckCallBack that will allow you to handle the boolean result if there is an already existing event with the qr code.
     */
    public void checkUnique(String qrContent, int mode, UniqueCheckCallBack callBack) {
        String field;
        if (mode == 0) {
            field = "qrCodePromo";
        } else {
            field = "qrCode";
        }
        eventsCollection
                .whereEqualTo(field, qrContent)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            callBack.onResult(task.getResult() == null || task.getResult().isEmpty());

                        } else {
                            Log.e("MainActivity", "Error checking existing Event QR codes");
                        }
                    }
                });
    }

    /**
     * \
     * Check if there exists an event with the matching promotional or checkin qr code
     *
     * @param qrContent a string that represents the content field of the QR code we are checking
     * @param mode an integer: 0 if we are checking promotional qr codes, 1 if we are checking checkin qr codes
     * @param callBack a class that implements the MatchingQRCallBack that will allow you to handle the event with the matching qr code
     */
    public void findEventWithQR(String qrContent, int mode, MatchingQRCallBack callBack) {
        String field;
        if (mode == 0) {
            field = "qrCodePromo";
        } else {
            field = "qrCode";
        }
        eventsCollection
                .whereEqualTo(field, qrContent)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (task.getResult() == null || task.getResult().isEmpty()) {
                                //if no event with a matching qr code was found, tell the callback
                                callBack.onNoResult();
                            } else{
                                for (DocumentSnapshot documentSnapshot : task.getResult()) {
                                    Event event = documentSnapshot.toObject((Event.class));
                                    if (event.getAnnouncements() == null){
                                        event.setAnnouncements(new ArrayList<String>());
                                    }
                                    if (event.getSignUps() == null){
                                        event.setSignUps(new ArrayList<String>());
                                    }
                                    if (event.getCheckIns() == null){
                                        event.setCheckIns(new ArrayList<String>());
                                    }
                                    event.setDocumentId(documentSnapshot.getId());

                                    callBack.onResult(event);
                                }
                            }

                        }
                    }
                });

    }
    /**
     * Retrieves all the events an user has signed up to
     *
     * @param user the user who as signed up to events
     * @param eventArrayList the list you want to add the events to
     */
    public void getEventsMadeByUser(Attendee user, ArrayList<Event> eventArrayList, EventListAdapter adapter) {
        eventsCollection
                .whereEqualTo("organizerId", user.getDocumentId())
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                            Event event = documentSnapshot.toObject(Event.class);
                            if (event.getAnnouncements() == null){
                                event.setAnnouncements(new ArrayList<String>());
                            }
                            if (event.getSignUps() == null){
                                event.setSignUps(new ArrayList<String>());
                            }
                            if (event.getCheckIns() == null){
                                event.setCheckIns(new ArrayList<String>());
                            }
                            event.setDocumentId(documentSnapshot.getId());
                            eventArrayList.add(event);
                        }
                        adapter.notifyDataSetChanged();
                        Log.d(eventsTAG, "Events successfully retrieved");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(eventsTAG, "Failed to retrieve events of user");
                    }
                });
    }

    public void addCheckIn(CheckIn checkIn) {
        checkInsCollection
                .add(checkIn)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        checkIn.setDocumentId(documentReference.getId());
                        updateCheckIn(checkIn);
                    }
                });
    }

    public void updateCheckIn(CheckIn checkIn) {
        checkInsCollection
                .document(checkIn.getDocumentId())
                .update("attendeeDocId", checkIn.getAttendeeDocId(),
                        "documentId", checkIn.getDocumentId(),
                        "eventDocId", checkIn.getEventDocId(), "longitude", checkIn.getLongitude(),
                        "latitude",checkIn.getLatitude() ,"numberOfCheckIns", checkIn.getNumberOfCheckIns())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(usersTAG, "CheckIn document updated successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(usersTAG, "Error while updating check in document", e);
                    }
                });
    }

    public void checkInAlreadyExists(String eventDocId, String attendeeDocId, UniqueCheckInCallBack callBack) {
        checkInsCollection
                .whereEqualTo("attendeeDocId", attendeeDocId)
                .whereEqualTo("eventDocId", eventDocId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        Log.d("checkInAlreadyExists", String.valueOf(queryDocumentSnapshots.isEmpty()));
                        CheckIn checkInToReturn = null;
                        if (!(queryDocumentSnapshots.isEmpty())){
                            if (queryDocumentSnapshots.getDocuments().size() == 1){
                                for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots.getDocuments()){
                                    checkInToReturn = documentSnapshot.toObject(CheckIn.class);
                                }
                            } else{
                                //TODO: handle this bad error, where there are duplicate checkins in firebase
                            }
                        }
                        //can return null if no checkin alredy exists, or if we get a bad checkin (duplicates exist)
                        callBack.onResult(queryDocumentSnapshots.isEmpty(), checkInToReturn);
                    }
                });
    }

    //i suggest this function be used primarily for new checkIns, because we need to also add it to the event

    /**
     * This functions creates a new checkIn for the user that is joining the event, and adds the documentID of the checkIn to the event's field in firebase
     * @param checkIn the object representing the checkIn. this holds the document ID of the event, and attendee that is checking in
     * @param event the event we are changing
     */
    public void addCheckInToEvent(CheckIn checkIn, Event event) {
        checkInsCollection
                .add(checkIn)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        checkIn.setDocumentId(documentReference.getId());
                        //update checkIn to get the document ID set in the field for future accesses
                        updateCheckIn(checkIn);
                        //we delete the signup from the event's field
                        event.deleteSignUp(checkIn.getAttendeeDocId());
                        //we add the checkIn and update our event :)
                        event.addCheckIn(checkIn.getDocumentId());
                        updateEvent(event);
                    }
                });

    }


    /**
     * Retrieves the paths of all posters
     *
     * @param postersPaths The list to be populated with poster paths
     * @param callback the OnFinishedCallback to invoke when firebase has completed it's operation
     */
    public void getPostersPaths(ArrayList<String> postersPaths, OnFinishedCallback callback) {
        storage.getReference().child("poster")
                .listAll()
                .addOnSuccessListener(new OnSuccessListener<ListResult>() {
                    @Override
                    public void onSuccess(ListResult listResult) {
                        for (StorageReference poster : listResult.getItems()) {
                            postersPaths.add(poster.getPath());
                        }
                        callback.onFinished();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(imagesTAG, "Error trying to get posters' paths" + e);
                    }
                });
    }

    /**
     * Retrieves the paths of all profile pictures
     *
     * @param profilesPaths The list to be populated with profile picture paths
     * @param callback the OnFinishedCallback to invoke when firebase has completed it's operation
     */
    public void getProfilePicturesPaths(ArrayList<String> profilesPaths, OnFinishedCallback callback) {
        storage.getReference().child("profile")
                .listAll()
                .addOnSuccessListener(new OnSuccessListener<ListResult>() {
                    @Override
                    public void onSuccess(ListResult listResult) {
                        for (StorageReference profile : listResult.getItems()) {
                            profilesPaths.add(profile.getPath());
                        }
                        callback.onFinished();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(imagesTAG, "Error trying to get profile pictures' paths" + e);
                    }
                });
    }

    /**
     * This function gets the paths of all poster images, and profile picture images
     * @param imagePaths The ArrayList to store all of the paths
     * @param callback The callback to invoke once firebase has finished this operation
     */
    public void getAllPicturesPaths(ArrayList<String> imagePaths, OnFinishedCallback callback){
        getPostersPaths(imagePaths, new OnFinishedCallback() {
            @Override
            public void onFinished() {
                getProfilePicturesPaths(imagePaths, new OnFinishedCallback() {
                    @Override
                    public void onFinished() {
                        callback.onFinished();
                    }
                });
            }
        });
    }

    /**
     * Retrieves an image from the database (administrator version)
     *
     * @param imagePath This is the path of the image we're trying to get (has the file extension)
     * @param callBack This callBack will be used to get back bitmap
     */
    public void adminRetrieveImage(String imagePath, GetBitmapCallBack callBack) {
        try {
            StorageReference storageRef = storage.getReference(imagePath);
            String path = imagePath.substring(0, imagePath.lastIndexOf("."));
            File localFile = File.createTempFile(path.split("/")[1], "jpg");
            storageRef.getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    callBack.onResult(BitmapFactory.decodeFile(localFile.getAbsolutePath()));
                    Log.d(imagesTAG, "Successfully retrieved image");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    Log.w(imagesTAG, "Failed to retrieve image: " + exception);
                }
            });
        } catch (IOException exception) {
            Log.e(imagesTAG, "Error trying to retrieve image: " + exception);
        }

    }

    /**
     * Deletes the document that represents the event and all check ins related to the event
     *
     * @param event the event to be deleted
     */
    public void deleteEvent(Event event) {
        eventsCollection
                .document(event.getDocumentId())
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(eventsTAG, "Successfully deleted the event");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(eventsTAG, "Failed to deleted event" + e);
                    }
                });

        checkInsCollection
                .whereEqualTo("eventDocId", event.getDocumentId())
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (DocumentSnapshot documentSnapshot : task.getResult()) {
                                checkInsCollection.document(documentSnapshot.getId()).delete();
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(eventsTAG, "Error trying to delete check Ins: " + e);
                    }
                });
    }

    /**
     * Deletes an image in the storage (administrator version) and clears the image path field of whatever user/event had that image
     *
     * @param imagePath This is the path of the image we're trying to get (has the file extension)
     */
    public void deleteImageAdmin(String imagePath, OnFinishedCallback callback) {
        storage.getReference()
                .child(imagePath)
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(imagesTAG, "Deleted image (admin) successfully");
                        //we look for the document (attendee or event) that has this imagePath in their posterPath field or profilePicturePathField
                        Log.d("test", imagePath.substring(1, imagePath.length() - 4));
                        if (imagePath.substring(1, 7).equals("poster")){
                            eventsCollection
                                    .whereEqualTo("posterPath", imagePath.substring(1, imagePath.length() - 4))
                                    .get()
                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                            if (task.isSuccessful()) {
                                                if (task.getResult() == null || task.getResult().isEmpty()) {

                                                } else {
                                                    for (DocumentSnapshot documentSnapshot: task.getResult()) {
                                                        Event event = documentSnapshot.toObject(Event.class);
                                                        event.setPosterPath(null);
                                                        updateEvent(event);

                                                    }
                                                }
                                                callback.onFinished();
                                            } else {
                                                Log.e("deleteImageAdmin", "Error trying to find the event that had this image.");
                                                callback.onFinished();
                                            }
                                        }
                                    });
                        } else{
                            usersCollection
                                    .whereEqualTo("profilePicturePath", imagePath.substring(1, imagePath.length() - 4))
                                    .get()
                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                            if (task.isSuccessful()) {
                                                if (task.getResult() == null || task.getResult().isEmpty()) {

                                                } else {
                                                    for (DocumentSnapshot documentSnapshot: task.getResult()) {
                                                        Attendee attendee = documentSnapshot.toObject(Attendee.class);
                                                        attendee.setProfilePicturePath(null);
                                                        updateUser(attendee);
                                                    }

                                                }
                                                callback.onFinished();
                                            } else {
                                                Log.e("deleteImageAdmin", "Error trying to find the attendee that had this image.");
                                                callback.onFinished();
                                            }
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(imagesTAG, "Failed to delete image: " + e);
                    }
                });
    }

    /**
     * Removes the profile of a user
     * @param attendee The user whose profile we want to remove
     */
    public void deleteProfile(Attendee attendee) {
        attendee.setEmail(null);
        attendee.setName("Guest24");
        if (attendee.getProfilePicturePath() != null){
            deleteImage(attendee.getProfilePicturePath());
        }
        attendee.setProfilePicturePath(null);

        updateUser(attendee);
    }

//    /**
//     * Gets users from the list of users field in each event
//     * Have to pass along the event class from EventDetailsFragment to AttendeeList so that it knows which event to get the checked-in users from
//     * @param event
//     */
//    public void getEventCheckedInUsers(Event event, ArrayList<Attendee> attendeeDataList, ArrayAdapter<Attendee> attendeeListAdapter) {
//        checkInsCollection
//                .whereEqualTo("eventDocId", event.getDocumentId()) //Finds document with the QR code of event clicked on
//                .get()
//                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
//                    @Override
//                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
//                        if (task.isSuccessful()) {
//                            for (QueryDocumentSnapshot document : task.getResult()) {
//                                // check if the checked in user has a name that exists
//                                if(document.get("name") != null){
//                                    String documentId = document.getId();
//                                    String id = (String) document.getData().get("id");
//                                    String name = (String) document.getData().get("name");
//                                    String email = (String) document.getData().get("email");
//                                    String profilePicturePath = (String) document.getData().get("profilePicturePath");
//                                    Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");
//                                    long checkins = (long) document.getData().get("numberOfCheckIns"); // changed to type long
//                                    Attendee attendee = new Attendee(id, documentId, name, email, profilePicturePath, geolocationOn, checkins);
//                                    attendeeDataList.add(attendee);
//                                }
//                                // otherwise add a default name
//                                else{
//                                    String documentId = document.getId();
//                                    String id = (String) document.getData().get("id");
//                                    long checkins = (long) document.getData().get("numberOfCheckIns"); // changed to type long
//                                    Attendee attendee = new Attendee("No Name", documentId, id, checkins);
//                                    attendeeDataList.add(attendee);
//                                }
//                            }
//                            attendeeListAdapter.notifyDataSetChanged();
//                        }
//                    }
//                })
//                .addOnFailureListener(new OnFailureListener() {
//                    @Override
//                    public void onFailure(@NonNull Exception e) {
//                        Log.w(eventsTAG, "Error trying to get the checked-in users: " + e);
//                    }
//                });
//    }

    /**
     * Gets users from the list of users field in each event
     * Have to pass along the event class from EventDetailsFragment to AttendeeList so that it knows which event to get the checked-in users from
     * @param event
     */
    public void getEventCheckedInUsers(Event event, ArrayList<Attendee> attendeeDataList, ArrayAdapter<Attendee> attendeeListAdapter) {
        for(Integer i = 0; i < event.getCheckIns().size(); i++) {
            checkInsCollection
                    .whereEqualTo("eventDocId", event.getDocumentId()) //Finds document with the QR code of event clicked on
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    // check if the checked in user has a name that exists

                                    String documentId = document.getId();
                                    String id = (String) document.getData().get("attendeeDocId");
                                    String name = (String) document.getData().get("name");
                                    String email = (String) document.getData().get("email");
                                    String profilePicturePath = (String) document.getData().get("profilePicturePath");
                                    Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");
                                    long checkins = (long) document.getData().get("numberOfCheckIns"); // changed to type long
                                    Attendee attendee = new Attendee(id, documentId, name, email, profilePicturePath, geolocationOn, checkins);
                                    attendeeDataList.add(attendee);

                                }
                                attendeeListAdapter.notifyDataSetChanged();
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w(eventsTAG, "Error trying to get the checked-in users: " + e);
                        }
                    });
        }
    }
    /**
     * Gets users from the list of signed up users field in each event
     * Have to pass along the event class from EventDetailsFragment to AttendeeList so that it knows which event to get the checked-in users from
     * @param event
     */
    public void getEventSignedUpUsers(Event event, ArrayList<Attendee> attendeeDataList, ArrayAdapter<Attendee> attendeeListAdapter) {
        for(Integer i = 0; i < event.getSignUps().size(); i++){
            usersCollection
                    .whereEqualTo("documentId", event.getSignUps().get(i))
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful()) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String documentId = document.getId();
                                    String id = (String) document.getData().get("id");
                                    String name = (String) document.getData().get("name");
                                    String email = (String) document.getData().get("email");
                                    String profilePicturePath = (String) document.getData().get("profilePicturePath");
                                    Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");

//                                Attendee attendee = document.toObject(Attendee.class);
                                    Attendee attendee = new Attendee(id, documentId, name, email, profilePicturePath, geolocationOn);
                                    attendeeDataList.add(attendee);
                                }
                                attendeeListAdapter.notifyDataSetChanged();
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w(eventsTAG, "Error trying to get the checked-in users: " + e);
                        }
                    });

        }
    }
  
     /**
     * This function looks for a document with matching admin login details to the user's input
     * @param username The username input by the user
     * @param password The password input by the user
     * @param callback The callback we invoked to tell if the user input valid or invalid admin login credentials
     */
    public void attemptAdminLogin(String username, String password, AttemptLoginCallback callback){
        adminLoginsCollection
                .whereEqualTo("user", username)
                .whereEqualTo("pass", password)

                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            //if there is no matching adminLogin document, we do not log the user in
                            if (task.getResult() == null || task.getResult().isEmpty()) {
                                callback.onNoResult();
                            } else {
                                //if there is a matching adminLogin document, we log the user in to the admin screen
                                callback.onResult();
                            }
                        } else {
                            Log.e("MainActivity", "Error trying to login");
                        }
                    }
                });

    }

    public void userCheckedIntoEvent(Attendee user, Event event, UniqueCheckCallBack callBack) {
        checkInsCollection
                .whereEqualTo("attendeeDocId", user.getDocumentId())
                .whereEqualTo("eventDocId", event.getDocumentId())
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        callBack.onResult(!queryDocumentSnapshots.isEmpty());
                    }
                });
    }

    public void getEventCheckedInUsersGeoLocation(Event event, ArrayList<String> attendeeDataList, ArrayList latitudeList, ArrayList longitudeList) {
        checkInsCollection
                .whereEqualTo("eventDocId", event.getDocumentId()) //Finds document with the QR code of event clicked on
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                // check if the checked in user has a name that exists
                                String documentId = document.getId();
                                double latitude = (double) document.getData().get("latitude");
                                double longitude = (double) document.getData().get("longitude");
//                                String id = (String) document.getData().get("id");
//                                String name = (String) document.getData().get("name");
//                                String email = (String) document.getData().get("email");
//                                String profilePicturePath = (String) document.getData().get("profilePicturePath");
//                                Boolean geolocationOn = (Boolean) document.getData().get("geolocationOn");
//                                long checkins = (long) document.getData().get("numberOfCheckIns"); // changed to type long
//                                Attendee attendee = new Attendee(id, documentId, name, email, profilePicturePath, geolocationOn, checkins);
                                attendeeDataList.add(documentId);
                                latitudeList.add(latitude);
                                longitudeList.add(longitude);

                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(eventsTAG, "Error trying to get the checked-in users: " + e);
                    }
                });
    }

    public void getToken(GetTokenCallback callback){
        messaging
                .getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        callback.onResult(task.getResult());
                        Log.d("token", task.getResult());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("token", "failed to get token");
                    }
                });
    }

    /**
     * This function subscribes a user to the topic that FCM will send new announcements to as push notifications
     * @param topicName The name of the topic in FCM that the user is subscribing to
     */
    public void subscribeAttendeeToEventTopic(String topicName){
        messaging
                .subscribeToTopic(topicName)
                .addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        Log.d("topic", "successfully subscribed user to event topic");

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("topic", "failed to subscribe user to event topic");
                    }
                });
    }

    public void getEventById(String eventId, GetEventCallback callback) {
        eventsCollection.document(eventId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                String id = document.getId();
                                String name = document.getString("name");
                                String organizerId = document.getString("organizerId");
                                String details = document.getString("details");
                                String location = document.getString("location");
                                String startDate = document.getString("startDate");
                                String endDate = document.getString("endDate");
                                Boolean geolocationOn = document.getBoolean("geolocationOn");
                                String posterPath = document.getString("posterPath");
                                String qrCode = document.getString("qrCode");
                                String qrCodePromo = document.getString("qrCodePromo");
                                String organizerToken = document.getString("organizerToken");
                                Long maxAttendeesLong = (Long) document.getData().get("maxAttendees");
                                Integer maxAttendees = maxAttendeesLong != null ? Math.toIntExact(maxAttendeesLong) : null;
                                ArrayList<String> announcements = (ArrayList<String>) document.get("announcements");
                                ArrayList<String> signUps = (ArrayList<String>) document.get("signUps");
                                ArrayList<String> checkIns = (ArrayList<String>) document.get("checkIns");

                                Event event = new Event(id, name, organizerId, details, location, startDate, endDate, geolocationOn, posterPath, qrCode, qrCodePromo, organizerToken, announcements, signUps, checkIns, maxAttendees);
                                callback.onSuccess(event);
                            } else {
                                Log.d(eventsTAG, "No such event exists");
                                callback.onFailure("No such event exists");
                            }
                        } else {
                            Log.d(eventsTAG, "Error getting event document: ", task.getException());
                            callback.onFailure("Error getting event document: " + task.getException().getMessage());
                        }
                    }
                });
    }


//    public static Task<Event> getEventById(String eventId) {
//        TaskCompletionSource<Event> taskCompletionSource = new TaskCompletionSource<>();
//
//        eventsCollection.document(eventId)
//                .get()
//                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
//                    @Override
//                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
//                        if (task.isSuccessful()) {
//                            DocumentSnapshot document = task.getResult();
//                            if (document.exists()) {
//                                String id = document.getId();
//                                String name = document.getString("name");
//                                String organizerId = document.getString("organizerId");
//                                String details = document.getString("details");
//                                String location = document.getString("location");
//                                String startDate = document.getString("startDate");
//                                String endDate = document.getString("endDate");
//                                Boolean geolocationOn = document.getBoolean("geolocationOn");
//                                String posterPath = document.getString("posterPath");
//                                String qrCode = document.getString("qrCode");
//                                String qrCodePromo = document.getString("qrCodePromo");
//                                String organizerToken = document.getString("organizerToken");
//                                ArrayList<String> announcements = (ArrayList<String>) document.get("announcements");
//                                ArrayList<String> signUps = (ArrayList<String>) document.get("signUps");
//                                ArrayList<String> checkIns = (ArrayList<String>) document.get("checkIns");
//
//                                Event event = new Event(id, name, organizerId, details, location, startDate, endDate, geolocationOn, posterPath, qrCode, qrCodePromo, organizerToken, announcements, signUps, checkIns);
//                                taskCompletionSource.setResult(event);
//                            } else {
//                                Log.d(eventsTAG, "No such event exists");
//                                taskCompletionSource.setException(new Exception("No such event exists"));
//                            }
//                        } else {
//                            Log.d(eventsTAG, "Error getting event document: ", task.getException());
//                            taskCompletionSource.setException(task.getException());
//                        }
//                    }
//                });
//
//        return taskCompletionSource.getTask();
//    }




    /**
     * Gets two lists, one for the CheckIn instances and one for the names of the attendees
     *
     * @param event the event we want to get the check in locations
     * @param callback The callback we invoked to return the two lists
     * */
    public void getGeolocations(Event event, GetMapMarkersCallback callback) {
        ArrayList<CheckIn> checkIns = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        AtomicInteger tasksCount = new AtomicInteger(event.getCheckIns().size());
        for (String checkIn : event.getCheckIns()) {
            checkInsCollection.document(checkIn).get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot1) {
                            String docId = (String) documentSnapshot1.get("attendeeDocId");
                            usersCollection.document(docId)
                                    .get()
                                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                        @Override
                                        public void onSuccess(DocumentSnapshot documentSnapshot2) {
                                            Boolean geolocationOn = (Boolean) documentSnapshot2.get("geolocationOn");
                                            if (Boolean.TRUE.equals(geolocationOn)) {
                                                checkIns.add(documentSnapshot1.toObject(CheckIn.class));
                                                names.add((String) documentSnapshot2.get("name"));
                                            }
                                            if (tasksCount.decrementAndGet() == 0) {
                                                callback.onResult(checkIns, names);
                                            }
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.w(checkInsTag, "Something went wrong" + e);
                                        }
                                    });
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w(checkInsTag, "Something went wrong" + e);
                        }
                    });
        }
    }
}
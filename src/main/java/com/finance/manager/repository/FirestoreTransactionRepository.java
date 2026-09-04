package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.finance.manager.model.Transaction;
import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class FirestoreTransactionRepository {
    private static final String PROJECT_ID="khatabook-finance-manager";
    private static final String COLLECTION_URL="https://firestore.googleapis.com/v1/projects/"+PROJECT_ID+"/databases/(default)/documents/users/";
    private final HttpClient httpClient=HttpClient.newHttpClient();

    public CompletableFuture<Transaction> addTransaction(AuthSession s,Transaction t){return CompletableFuture.supplyAsync(()->{validateSession(s); HttpRequest r=authorizedRequest(collectionUrl(s),s.getIdToken()).POST(HttpRequest.BodyPublishers.ofString(transactionDocument(t).toString())).build(); JsonObject j=JsonParser.parseString(sendAndReturn(r).body()).getAsJsonObject(); String n=j.has("name")?j.get("name").getAsString():""; String id=n.isBlank()?"":n.substring(n.lastIndexOf('/')+1); t.setId(id); return t;});}
    public CompletableFuture<List<Transaction>> getTransactions(AuthSession s){return CompletableFuture.supplyAsync(()->{validateSession(s); JsonObject root=JsonParser.parseString(sendAndReturn(authorizedRequest(collectionUrl(s),s.getIdToken()).GET().build()).body()).getAsJsonObject(); List<Transaction> out=new ArrayList<>(); if(!root.has("documents"))return out; for(JsonElement e:root.getAsJsonArray("documents")){JsonObject d=e.getAsJsonObject(),f=d.getAsJsonObject("fields");String n=d.has("name")?d.get("name").getAsString():"";String id=n.isBlank()?"":n.substring(n.lastIndexOf('/')+1);Transaction t=new Transaction(id,Transaction.Type.valueOf(stringValue(f,"type","EXPENSE")),doubleValue(f,"amount"),stringValue(f,"category","Other"),stringValue(f,"description",""),LocalDate.parse(stringValue(f,"date",LocalDate.now().toString())),stringValue(f,"personName",""));t.setAttachmentNames(listValue(f,"attachmentNames"));t.setAttachmentPaths(listValue(f,"attachmentPaths"));out.add(t);}out.sort((a,b)->b.getDate().compareTo(a.getDate()));return out;});}
    public CompletableFuture<Transaction> updateTransaction(AuthSession s,Transaction t){return CompletableFuture.supplyAsync(()->{validateSession(s);validateTransactionId(t);sendAndReturn(authorizedRequest(documentUrl(s,t.getId()),s.getIdToken()).method("PATCH",HttpRequest.BodyPublishers.ofString(transactionDocument(t).toString())).build());return t;});}
    public CompletableFuture<Void> deleteTransaction(AuthSession s,String id){return CompletableFuture.runAsync(()->{validateSession(s);if(id==null||id.isBlank())throw new RuntimeException("Transaction ID is missing.");sendAndReturn(authorizedRequest(documentUrl(s,id),s.getIdToken()).DELETE().build());});}
    private JsonObject transactionDocument(Transaction t){JsonObject f=new JsonObject();f.add("type",stringField(t.getType().name()));f.add("amount",doubleField(t.getAmount()));f.add("category",stringField(t.getCategory()));f.add("description",stringField(t.getDescription()));f.add("date",stringField(t.getDate().toString()));f.add("personName",stringField(t.getPersonName()));f.add("attachmentNames",stringField(new Gson().toJson(t.getAttachmentNames())));f.add("attachmentPaths",stringField(new Gson().toJson(t.getAttachmentPaths())));JsonObject d=new JsonObject();d.add("fields",f);return d;}
    private String collectionUrl(AuthSession s){return COLLECTION_URL+URLEncoder.encode(s.getLocalId(),StandardCharsets.UTF_8)+"/transactions?key="+FirebaseConfig.getWebApiKey();}
    private String documentUrl(AuthSession s,String id){return collectionUrl(s).substring(0,collectionUrl(s).indexOf("?key="))+"/"+URLEncoder.encode(id,StandardCharsets.UTF_8)+"?key="+FirebaseConfig.getWebApiKey();}
    private JsonObject stringField(String v){JsonObject f=new JsonObject();f.addProperty("stringValue",v==null?"":v);return f;} private JsonObject doubleField(double v){JsonObject f=new JsonObject();f.addProperty("doubleValue",v);return f;}
    private String stringValue(JsonObject f,String n,String fallback){return f.has(n)&&f.getAsJsonObject(n).has("stringValue")?f.getAsJsonObject(n).get("stringValue").getAsString():fallback;}
    private double doubleValue(JsonObject f,String n){if(!f.has(n))return 0;JsonObject x=f.getAsJsonObject(n);if(x.has("doubleValue"))return x.get("doubleValue").getAsDouble();if(x.has("integerValue"))return x.get("integerValue").getAsDouble();return 0;}
    private List<String> listValue(JsonObject f,String n){try{String json=stringValue(f,n,"[]");JsonArray a=JsonParser.parseString(json).getAsJsonArray();List<String> out=new ArrayList<>();for(JsonElement e:a)out.add(e.getAsString());return out;}catch(Exception e){return new ArrayList<>();}}
    private HttpResponse<String> sendAndReturn(HttpRequest r){try{HttpResponse<String> x=httpClient.send(r,HttpResponse.BodyHandlers.ofString());ensureSuccess(x);return x;}catch(IOException e){throw new RuntimeException("Unable to connect to Firestore.",e);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException("Firestore request was interrupted.",e);}}
    private HttpRequest.Builder authorizedRequest(String url,String token){return HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization","Bearer "+token).header("Content-Type","application/json");}
    private void validateSession(AuthSession s){if(s==null)throw new RuntimeException("No authenticated session.");if(s.getIdToken()==null||s.getIdToken().isBlank())throw new RuntimeException("Authentication token is missing.");if(s.getLocalId()==null||s.getLocalId().isBlank())throw new RuntimeException("Firebase user ID is missing.");}
    private void validateTransactionId(Transaction t){if(t==null||t.getId()==null||t.getId().isBlank())throw new RuntimeException("Transaction ID is missing.");}
    private void ensureSuccess(HttpResponse<String> r){if(r.statusCode()<200||r.statusCode()>=300)throw new RuntimeException("Firestore error "+r.statusCode()+": "+r.body());}
}

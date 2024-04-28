package com.envn8.app.controller;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

import com.envn8.app.models.Documents;
import com.envn8.app.models.User;
import com.envn8.app.service.DocumentService;
import com.envn8.app.service.UserService;

import lombok.RequiredArgsConstructor;

import com.envn8.app.payload.request.DocumentRequest;
import com.envn8.app.payload.request.ShareDocumentRequest;
import com.envn8.app.security.config.JwtService;
import org.slf4j.Logger;

@RestController
@RequestMapping("/dc")
@RequiredArgsConstructor
public class DocumentController {
    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;
    @Autowired // can NOT use @RequiredArgsConstructor here, @Autowired is needed
    private UserService userService;
    @Autowired
    private JwtService jwtService;

    @PostMapping("/create")
    public ResponseEntity<?> createDocument_temp(@RequestBody DocumentRequest documentRequest,
            @RequestHeader("Authorization") String token) {
        System.out.println("at the start of createDocument_temp()");
        if (token == null || token.isEmpty()) {
            return new ResponseEntity<>("Token is null or empty", HttpStatus.UNAUTHORIZED);
        }
        String actualToken = token.replace("Bearer ", "");
        String username = jwtService.extractUsername(actualToken);

        // Load the user details
        UserDetails userDetails = userService.getUserByUsername(username);
        if (userDetails == null) {
            return new ResponseEntity<>("User not found", HttpStatus.UNAUTHORIZED);
        }

        // Create the document
        Documents document = new Documents();
        document.setContent(documentRequest.getContent());
        document.setTitle(documentRequest.getTitle());
        document.setType(documentRequest.getType());

        // Get the User object from the UserDetails
        User user = (User) userDetails;
        document.setOwner(user);

        // Add the document to the user's documents
        user.getDocuments().add(document);

        // Save the document and the user
        documentService.createDocument(document);
        userService.saveUser(user);

        return new ResponseEntity<>(document, HttpStatus.CREATED);
    }

    // Open a document
    @GetMapping("/view/{id}")
    public ResponseEntity<Documents> viewDocument_temp(@PathVariable String id,
            @RequestHeader("Authorization") String token) {
        // System.out.println("hello it's me " + token);
        String actualToken = token.replace("Bearer ", "");
        String username = jwtService.extractUsername(actualToken);
        Optional<Documents> documentOptional = documentService.getDocumentById(id);
        if (documentOptional.isPresent()) {
            Documents document = documentOptional.get();
            if (document.getType().equals("public") || document.getOwner().getUsername().equals(username)
                    || document.getSharedWith().contains(userService.getUserByUsername(username))) {
                System.out.println("***********Document info:************");
                return new ResponseEntity<>(documentOptional.get(), HttpStatus.OK);
            } else {
                System.out.println("Forbidden access to document!");
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }
        } else {
            System.out.println("Document not found!");
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/viewAll")
    public ResponseEntity<Iterable<Documents>> viewAllDocuments(@RequestHeader("Authorization") String token) {
        System.out.println("at the start of viewAllDocuments()");
        String actualToken = token.replace("Bearer ", "");
        String username = jwtService.extractUsername(actualToken);
        System.out.println("username: " + username + " token: " + actualToken);
        User user = userService.getUserByUsername(username); // Get
        Iterable<Documents> documents = user.getDocuments();
        // size of documents
        int size = (int) documents.spliterator().getExactSizeIfKnown();
        System.out.println("Size of documents: " + size);
        if (size != 0) {
            System.out.println("Documents is not null");
            for (Documents doc : documents) {
                System.out.println("Document title: " + doc.getTitle());
            }
        } else {
            logger.error("Documents is null");
        }
        System.out.println("log at the end of viewAllDocuments()");

        return new ResponseEntity<>(documents, HttpStatus.OK);
    }

    // function to delete a document
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable String id,
            @RequestHeader("Authorization") String token) {
        String actualToken = token.replace("Bearer ", "");
        String username = jwtService.extractUsername(actualToken);
        Optional<Documents> documentOptional = documentService.getDocumentById(id);
        if (documentOptional.isPresent()) {
            Documents document = documentOptional.get();
            if (document.getOwner().getUsername().equals(username)) {
                documentService.deleteDocument(id);
                return new ResponseEntity<>("Document deleted successfully", HttpStatus.OK);
            } else {
                return new ResponseEntity<>("You are not the owner of the document", HttpStatus.FORBIDDEN);
            }
        } else {
            return new ResponseEntity<>("Document not found", HttpStatus.NOT_FOUND);
        }
    }

    // function to share a document
    @PostMapping("/share/{id}")
    public ResponseEntity<?> shareDocument(@PathVariable String id, @RequestBody ShareDocumentRequest shareRequest,
            @RequestHeader("Authorization") String token) {
        String actualToken = token.replace("Bearer ", "");
        String ownerUsername = jwtService.extractUsername(actualToken);
        Optional<Documents> documentOptional = documentService.getDocumentById(id);
        if (documentOptional.isPresent()) {
            Documents document = documentOptional.get();
            if (document.getOwner().getUsername().equals(ownerUsername)) {
                User user = userService.getUserByUsername(shareRequest.getUsername());
                if (user != null) {
                    document.getSharedWith().add(user);
                    document.getPermissions().put(user.getId(), shareRequest.getPermission());
                    documentService.createDocument(document);
                    return new ResponseEntity<>("Document shared successfully", HttpStatus.OK);
                } else {
                    return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
                }
            } else {
                return new ResponseEntity<>("You are not the owner of the document", HttpStatus.FORBIDDEN);
            }
        } else {
            return new ResponseEntity<>("Document not found", HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/getDocumentOwner/{id}")
    public ResponseEntity<?> getDocumentOwner(@PathVariable String id) {
        System.out.println("getDocumentOwner() called");
        Optional<Documents> documentOptional = documentService.getDocumentById(id);
        if (documentOptional.isPresent()) {
            Documents document = documentOptional.get();
            return new ResponseEntity<>(document.getOwner().getUsername(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Document not found", HttpStatus.NOT_FOUND);
        }
    }

    // rename a document
    //might want to make a separate request for this, instead of using the same DocumentRequest object
    @PutMapping("/rename/{id}")
    public ResponseEntity<?> renameDocument(@PathVariable String id,
            @RequestBody DocumentRequest documentRequest,
            @RequestHeader("Authorization") String token) {
        String actualToken = token.replace("Bearer ", "");
        String username = jwtService.extractUsername(actualToken);
        Optional<Documents> documentOptional = documentService.getDocumentById(id);
        if (documentOptional.isPresent()) {
            Documents document = documentOptional.get();
            if (document.getOwner().getUsername().equals(username)) {
                document.setTitle(documentRequest.getTitle());
                documentService.updateDocument(document);
                return new ResponseEntity<>("Document renamed successfully", HttpStatus.OK);
            } else {
                return new ResponseEntity<>("You are not the owner of the document", HttpStatus.FORBIDDEN);
            }
        } else {
            return new ResponseEntity<>("Document not found", HttpStatus.NOT_FOUND);
        }
    }

}

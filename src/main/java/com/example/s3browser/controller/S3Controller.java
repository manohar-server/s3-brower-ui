package com.example.s3browser.controller;

import javax.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class S3Controller {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/setCredentials")
    public String setCredentials(HttpSession session,
                                 @RequestParam String accessKey,
                                 @RequestParam String secretKey
                                 ) {
        session.setAttribute("accessKey", accessKey);
        session.setAttribute("secretKey", secretKey);
        return "redirect:/buckets";
    }

    @GetMapping("/buckets")
    public String listBuckets(HttpSession session, Model model) {
        S3Client s3 = getS3Client(session);
        List<String> buckets = s3.listBuckets().buckets().stream()
                .map(Bucket::name).collect(Collectors.toList());
        model.addAttribute("buckets", buckets);
        return "buckets";
    }

    @GetMapping("/buckets/{bucketName}")
    public String listObjects(@PathVariable String bucketName,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Model model, HttpSession session) {
        S3Client s3 = getS3Client(session);
        List<S3Object> allObjects = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName).build()).contents();

        int totalPages = (int) Math.ceil((double) allObjects.size() / size);
        int start = (page - 1) * size;
        int end = Math.min(start + size, allObjects.size());
        List<S3Object> paginated = allObjects.subList(start, end);

        model.addAttribute("bucketName", bucketName);
        model.addAttribute("objects", paginated);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        return "objects";
    }

    @GetMapping("/download/{bucketName}/{key}")
    @ResponseBody
    public ResponseEntity<byte[]> downloadObject(@PathVariable String bucketName,
                                                 @PathVariable String key,
                                                 HttpSession session) {
        S3Client s3 = getS3Client(session);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object = s3.getObject(getObjectRequest);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = s3Object.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(buffer.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    private S3Client getS3Client(HttpSession session) {
        String accessKey = (String) session.getAttribute("accessKey");
        String secretKey = (String) session.getAttribute("secretKey");
        return S3Client.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }
}
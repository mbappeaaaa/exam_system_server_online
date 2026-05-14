package com.atguigu.exam.service.impl;

import com.atguigu.exam.config.properties.MinioProperties;
import com.atguigu.exam.service.FileUploadService;
import io.minio.*;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * projectName: com.atguigu.exam.service.impl
 *
 * @author: 赵伟风
 * description:
 */
@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioProperties minioProperties;
    @Override
    public String uploadFile(String folder, MultipartFile file) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        //1.连接minio的客户端
        //2.判断桶是否存在
        boolean bucketedExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucketName()).build());
        if (!bucketedExists) {
            //3.不存在创建hello-minio桶
            minioClient.makeBucket(MakeBucketArgs.builder().bucket("hello-minio").build());
            //设置hello-minio桶的访问权限
            String policy = """
                        {
                          "Statement" : [ {
                            "Action" : "s3:GetObject",
                            "Effect" : "Allow",
                            "Principal" : "*",
                            "Resource" : "arn:aws:s3:::exam-system/*"
                          } ],
                          "Version" : "2012-10-17"
                        }""".formatted(minioProperties.getBucketName());
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build());
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(minioProperties.getBucketName()).config(policy).build());
        }
        //4.上传文件:防止重复+日期
        String ObjectName=folder+"/"+
                          new SimpleDateFormat("yyyyMMdd").format(new Date())+"/"+
                          UUID.randomUUID().toString().replaceAll("-","")+
                          file.getOriginalFilename();
        minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .contentType(file.getContentType())
                        .object(ObjectName)
                         //参数一：上传文件的输入流
                         //参数二：上传文件的字节大小
                         //参数三：是否进行切割。以及切割文件的大小-1minio进行智能切割
                        .stream(file.getInputStream(), file.getSize(), -1)
                .build());
        //5.拼接回显地址：URL=端点+桶+对象名
        String URL=String.join("/",minioProperties.getEndpoint(), minioProperties.getBucketName(),ObjectName);
        log.info("文件辉县地址：{}",URL);
        return URL;
    }
}

package edu.duke.ece568.erss.amazon;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.GeneratedMessageV3;

import java.io.*;

public class Utils {

    public static <T extends GeneratedMessageV3> boolean sendMsgTo(T msg, OutputStream out) {
        try {
            byte[] data = msg.toByteArray();
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(out);
            codedOutputStream.writeUInt32NoTag(data.length);
            codedOutputStream.writeRawBytes(data);
            // NOTE!!! always flush the result to stream
            codedOutputStream.flush();
            return true;
        }catch (IOException e){
            System.err.println(e.toString());
            return false;
        }
    }

    public static <T extends GeneratedMessageV3.Builder<?>> boolean recvMsgFrom(T msg, InputStream in) {
        try {
            CodedInputStream codedInputStream = CodedInputStream.newInstance(in);
            int len = codedInputStream.readRawVarint32();
            int oldLimit = codedInputStream.pushLimit(len);
            msg.mergeFrom(codedInputStream);
            codedInputStream.popLimit(oldLimit);
            return true;
        }catch (IOException e){
            System.err.println(e.toString());
            return false;
        }
    }
}

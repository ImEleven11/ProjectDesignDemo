package cn.edu.uestc.projectdesign.demo.api.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}

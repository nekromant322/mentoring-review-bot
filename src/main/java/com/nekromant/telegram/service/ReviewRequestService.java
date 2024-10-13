package com.nekromant.telegram.service;

import com.nekromant.telegram.model.ReviewRequest;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReviewRequestService {
    public ReviewRequest getTemporaryReviewRequest(User user, String[] arguments, String studentChatId) {
        ReviewRequest reviewRequest = new ReviewRequest();

        reviewRequest.setStudentUserName(user.getUserName());
        reviewRequest.setStudentChatId(studentChatId);
        reviewRequest.setTitle(parseTitle(arguments));
        reviewRequest.setTimeSlots(parseTimeSlots(arguments));
        return reviewRequest;
    }

    private Set<Integer> parseTimeSlots(String[] strings) {
        Set<Integer> timeSlots = new HashSet<>();
        for (String string : strings) {
            if (!string.toLowerCase().contains("тема")) {
                timeSlots.add(Integer.parseInt(string));
                if (Integer.parseInt(string) > 24 || Integer.parseInt(string) < 0) {
                    throw new InvalidParameterException("Неверное значение часов — должно быть от 0 до 23");
                }
            } else {
                return timeSlots;
            }
        }
        return timeSlots;
    }

    private String parseTitle(String[] strings) {
        for (int i = 0; i < strings.length; i++) {
            if (strings[i].toLowerCase().contains("тема")) {
                return Arrays.stream(strings).skip(i).collect(Collectors.joining(" "));
            }
        }
        return "";
    }
}
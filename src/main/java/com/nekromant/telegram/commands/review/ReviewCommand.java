package com.nekromant.telegram.commands.review;


import com.nekromant.telegram.commands.MentoringReviewCommand;
import com.nekromant.telegram.model.ReviewRequest;
import com.nekromant.telegram.service.ReviewRequestService;
import com.nekromant.telegram.service.UserInfoService;
import com.nekromant.telegram.utils.SendMessageFactory;
import com.nekromant.telegram.utils.ValidationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.security.InvalidParameterException;

import static com.nekromant.telegram.contants.Command.REVIEW;
import static com.nekromant.telegram.contants.MessageContants.*;

@Slf4j
@Component
public class ReviewCommand extends MentoringReviewCommand {

    @Autowired
    private SendMessageFactory sendMessageFactory;
    @Autowired
    private ReviewRequestDateTimePicker reviewRequestDateTimePicker;
    @Autowired
    private ReviewRequestService reviewRequestService;
    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    public ReviewCommand() {
        super(REVIEW.getAlias(), REVIEW.getDescription());
    }

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] arguments) {
        userInfoService.initializeUserInfo(chat, user);

        if (chat.isGroupChat() || chat.isSuperGroupChat()) {
            sendAnswer(chat.getId().toString(), GROUP_CHAT_IS_NOT_SUPPORTED, absSender, user);
        } else {
            SendMessage message = new SendMessage();
            String studentChatId = chat.getId().toString();
            message.setChatId(studentChatId);

            try {
                ValidationUtils.validateArgumentsNumber(arguments);
                ReviewRequest reviewRequest = reviewRequestService.getTemporaryReviewRequest(user, arguments);

                log.info("Сохранение нового реквеста {}", reviewRequest);
                reviewRequestService.save(reviewRequest);
                absSender.execute(reviewRequestDateTimePicker.getDatePickerSendMessage(reviewRequest));
            } catch (NumberFormatException e) {
                String firstArgument = arguments[0];
                if (firstArgument.contains("сегодня") || firstArgument.contains("завтра")) {
                    message.setText("ФОРМАТ ОБНОВИЛСЯ, НЕ НУЖНО ПИСАТЬ [сегодня|завтра], ПОСМОТРИ ВНИМАТЕЛЬНЕЕ НА ПРИМЕР\n\n" + REVIEW_HELP_MESSAGE);
                } else {
                    log.error("Таймслот должен быть указан целым числом. {}", e.getMessage());
                    message.setText("Таймслот должен быть указан целым числом\n" + REVIEW_HELP_MESSAGE);
                }
                execute(absSender, message, user);
            } catch (InvalidParameterException e) {
                log.error("Неверный аргумент был передан в команду. {}", e.getMessage());
                message.setText(e.getMessage() + "\n" + REVIEW_HELP_MESSAGE);
                execute(absSender, message, user);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                message.setText(ERROR + REVIEW_HELP_MESSAGE);
                execute(absSender, message, user);
            }
        }
    }

    private void sendAnswer(String chatId, String text, AbsSender absSender, User user) {
        SendMessage message = sendMessageFactory.create(chatId, text);
        execute(absSender, message, user);
    }
}

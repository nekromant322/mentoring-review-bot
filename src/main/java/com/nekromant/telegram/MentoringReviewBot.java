package com.nekromant.telegram;

import com.nekromant.telegram.commands.MentoringReviewCommand;
import com.nekromant.telegram.contants.CallBack;
import com.nekromant.telegram.model.Report;
import com.nekromant.telegram.model.ReviewRequest;
import com.nekromant.telegram.repository.ReportRepository;
import com.nekromant.telegram.repository.ReviewRequestRepository;
import com.nekromant.telegram.service.SpecialChatService;
import com.nekromant.telegram.service.UserInfoService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.security.InvalidParameterException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static com.nekromant.telegram.contants.MessageContants.*;
import static com.nekromant.telegram.utils.FormatterUtils.defaultDateFormatter;
import static com.nekromant.telegram.utils.FormatterUtils.defaultDateTimeFormatter;


@Component
@Slf4j
public class MentoringReviewBot extends TelegramLongPollingCommandBot {

    @Value("${bot.name}")
    private String botName;

    @Value("${bot.token}")
    private String botToken;

    @Autowired
    private SpecialChatService specialChatService;

    @Autowired
    private ReviewRequestRepository reviewRequestRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    public MentoringReviewBot(List<MentoringReviewCommand> allCommands) {
        super();
        allCommands.forEach(this::register);
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @SneakyThrows
    @Override
    public void processNonCommandUpdate(Update update) {
        if (update.hasMessage() && update.getMessage().isUserMessage()) {
            if (!(update.getMessage().getChatId().toString().equals(specialChatService.getMentorsChatId()) ||
                    update.getMessage().getChatId().toString().equals(specialChatService.getReportsChatId()) ||
                    update.getMessage().getChat().getTitle().equals("java-кумунити"))) { //todo add chatId to special chats
                sendMessage(update);
            }
        } else if (update.hasCallbackQuery()) {

            String callBackData = update.getCallbackQuery().getData();
            SendMessage message = new SendMessage();
            SendMessage messageForMentors = new SendMessage();
            messageForMentors.setChatId(specialChatService.getMentorsChatId());

            String callbackCommandName = callBackData.split(" ")[0];
            if (callbackCommandName.equalsIgnoreCase(CallBack.APPROVE.getAlias())) {
                Long reviewId = Long.parseLong(callBackData.split(" ")[1]);
                int timeSlot = Integer.parseInt(callBackData.split(" ")[2]);
                ReviewRequest review = reviewRequestRepository.findById(reviewId).orElseThrow(InvalidParameterException::new);
                review.setBookedDateTime(LocalDateTime.of(review.getDate(), LocalTime.of(timeSlot, 0)));
                review.setMentorUserName(update.getCallbackQuery().getFrom().getUserName());
                reviewRequestRepository.save(review);
                message.setChatId(review.getStudentChatId());

                message.setText(String.format(REVIEW_BOOKED, review.getMentorUserName(),
                        review.getBookedDateTime().format(defaultDateTimeFormatter()), review.getTitle()));

                messageForMentors.setText(String.format(REVIEW_APPROVED, update.getCallbackQuery().getFrom().getUserName(),
                        review.getStudentUserName(), review.getBookedDateTime().format(defaultDateTimeFormatter())));
                deleteMessageMarkUp(review.getPollMessageId(), specialChatService.getMentorsChatId());
            }
            if (callbackCommandName.equalsIgnoreCase(CallBack.DENY.getAlias())) {
                Long reviewId = Long.parseLong(callBackData.split(" ")[1]);

                ReviewRequest review = reviewRequestRepository.findById(reviewId).orElseThrow(InvalidParameterException::new);
                message.setChatId(review.getStudentChatId());
                message.setText(String.format(NOBODY_CAN_MAKE_REVIEW, review.getDate().format(defaultDateFormatter())) +
                        review.getTimeSlots().stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(":00, ")) + ":00" + "\n");

                reviewRequestRepository.deleteById(reviewId);

                messageForMentors.setText(String.format(SOMEBODY_DENIED_REVIEW, update.getCallbackQuery().getFrom().getUserName(),
                        review.getStudentUserName()));
                deleteMessageMarkUp(review.getPollMessageId(), specialChatService.getMentorsChatId());
            }
            if (callbackCommandName.equalsIgnoreCase(CallBack.TODAY.getAlias())) {
                String date = callBackData.split(" ")[1];
                Report report = reportRepository.findById(Long.parseLong(callBackData.split(" ")[2])).orElseThrow(InvalidParameterException::new);
                if (date.equalsIgnoreCase("Сегодня")) {
                    report.setDate(LocalDate.now(ZoneId.of("Europe/Moscow")));
                    validateAndSaveReportDate(message, report);
                } else {
                    throw new InvalidParameterException("Неверный ответ в колбэке: " + date);
                }

                setChatIdForUser(update, message);
                deleteReportDatePickerMessage(update);
            }
            if (callbackCommandName.equalsIgnoreCase(CallBack.YESTERDAY.getAlias())) {
                String date = callBackData.split(" ")[1];
                Report report = reportRepository.findById(Long.parseLong(callBackData.split(" ")[2])).orElseThrow(InvalidParameterException::new);
                if (date.equalsIgnoreCase("Вчера")) {
                    report.setDate(LocalDate.now(ZoneId.of("Europe/Moscow")).minusDays(1));
                    validateAndSaveReportDate(message, report);
                } else {
                    throw new InvalidParameterException("Ошибка в колбэке: " + date);
                }

                setChatIdForUser(update, message);
                sendMessage(specialChatService.getReportsChatId(), "@" + report.getStudentUserName() + "\n" + report.getDate().format(defaultDateFormatter()) + "\n" + report.getHours() +
                    "\n" + report.getTitle());
                deleteReportDatePickerMessage(update);
            }
            if (callbackCommandName.equalsIgnoreCase(CallBack.DENY_REPORT.getAlias())) {
                Long reportId = Long.parseLong(callBackData.split(" ")[1]);

                Report report = reportRepository.findById(reportId).orElseThrow(InvalidParameterException::new);
                message.setChatId(userInfoService.getUserInfo(report.getStudentUserName()).getChatId().toString());
                message.setText("Отправка отчёта отменена");
                reportRepository.deleteById(reportId);

                deleteReportDatePickerMessage(update);
            }
            try {
                execute(message);
                if (messageForMentors.getText() != null) {
                    execute(messageForMentors);
                }
            } catch (InvalidParameterException e) {
                deleteReportDatePickerMessage(update);
            } catch (TelegramApiException e) {
                log.error(e.getMessage(), e);
            }
        } else if (update.hasEditedMessage()) {
            processEditedMessageUpdate(update);
        }
    }

    private void validateAndSaveReportDate(SendMessage messageForUser, Report report) {
        if (reportRepository.existsReportByDateAndStudentUserName(report.getDate(), report.getStudentUserName())) {
            messageForUser.setText(TOO_MANY_REPORTS);
            reportRepository.deleteById(report.getId());
        }  else {
            messageForUser.setText("@" + report.getStudentUserName() + "\n" + report.getDate().format(defaultDateFormatter()) + "\n" + report.getHours() +
                    "\n" + report.getTitle());
            reportRepository.save(report);
        }
    }

    private void setChatIdForUser(Update update, SendMessage messageForUser) {
        messageForUser.setChatId(update.getCallbackQuery().getMessage().getChatId().toString());
    }

    private void deleteReportDatePickerMessage(Update update) throws TelegramApiException {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(update.getCallbackQuery().getMessage().getChatId().toString());
        deleteMessage.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
        execute(deleteMessage);
    }

    public void processEditedMessageUpdate(Update update) {
        update.setMessage(update.getEditedMessage());
        super.onUpdateReceived(update);
    }

    @SneakyThrows
    private void deleteMessageMarkUp(Integer messageId, String chatId) {
        EditMessageReplyMarkup message = new EditMessageReplyMarkup();
        message.setChatId(chatId);
        message.setMessageId(messageId);
        message.setReplyMarkup(null);
        execute(message);

    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    private void sendMessage(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(update.getMessage().getChatId()));
        sendMessage.setText(UNKNOWN_COMMAND);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }

    @SneakyThrows
    public void sendMessage(String chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            //убирает превьюшки ссылок
            message.disableWebPagePreview();
            message.setText(text);
            message.setChatId(chatId);
            execute(message);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения " + e.getMessage());
        }

    }
}

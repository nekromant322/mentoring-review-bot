package com.nekromant.telegram.utils;

import com.nekromant.telegram.model.PublicOffer;
import com.nekromant.telegram.repository.PublicOfferRepository;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

@Component
public class PresaveOffer {
    @Autowired
    private PublicOfferRepository offerRepository;

    @PostConstruct
    public void saveOffer() throws IOException {
        Collection<PublicOffer> offers = (Collection<PublicOffer>) offerRepository.findAll();
        if (offers.isEmpty()) {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("static/others/pub_offer.pdf");
            byte[] fileBytes = IOUtils.toByteArray(inputStream);
            PublicOffer publicOffer = new PublicOffer();
            publicOffer.setOfferPdf(fileBytes);
            publicOffer.setId(1L);
            offerRepository.save(publicOffer);
        }
    }
}

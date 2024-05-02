package com.nekromant.telegram.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ResumeAnalysisRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_pdf")
    @Lob
    private byte[] CVPdf;

    @Column(name = "tg_name")
    private String tgName;

    private String phone;

    private String lifePayNumber;

    @Override
    public String toString() {
        return "ResumeAnalysisRequest (tgName=" + this.getTgName()
                + ", phone=" + this.getPhone()
                + ", lifePayNumber=" + this.getLifePayNumber()
                + ", CVPdf.length=" + getCVPdf().length + ")";
    }
}

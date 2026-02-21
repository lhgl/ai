package br.com.ai.connector.adapter.in.rest;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.File;

public class FileRequestForm {

    @RestForm("prompt")
    @PartType("text/plain")
    public String prompt;

    @RestForm("file")
    @PartType("application/octet-stream")
    public File file;
}

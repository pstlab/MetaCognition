package it.cnr.istc.pst.mc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Context reasoner of the MetaCognition 
 */
@Controller
@SpringBootApplication
public class ContextReasonerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContextReasonerApplication.class, args);
	}

	/**
	 * 
	 * @return
	 */
    @GetMapping(
        value = "/",
        produces = "text/html")
    public String getMethodName() {
        return "forward:/sparql_gui.html";
    }

}

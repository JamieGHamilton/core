package org.richfaces.integration.partialViewContext;

import static org.jboss.arquillian.warp.jsf.Phase.INVOKE_APPLICATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;

import javax.faces.context.FacesContext;
import javax.faces.event.ExceptionQueuedEvent;
import javax.faces.event.ExceptionQueuedEventContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.warp.Activity;
import org.jboss.arquillian.warp.Inspection;
import org.jboss.arquillian.warp.Warp;
import org.jboss.arquillian.warp.WarpTest;
import org.jboss.arquillian.warp.jsf.BeforePhase;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.richfaces.integration.CoreDeployment;
import org.richfaces.shrinkwrap.descriptor.FaceletAsset;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Tests that the exception in ajax requests ends partial-response correctly (RF-12893)
 */
@RunAsClient
@RunWith(Arquillian.class)
@WarpTest
public class AjaxExceptionHandlingTest {

    @Drone
    private WebDriver browser;

    @ArquillianResource
    private URL contextPath;

    @FindBy(id = "button")
    private WebElement button;

    @FindBy(tagName = "body")
    private WebElement body;

    @Deployment
    public static WebArchive createDeployment() {
        CoreDeployment deployment = new CoreDeployment(AjaxExceptionHandlingTest.class);

        deployment.withWholeCore();

        addIndexPage(deployment);

        return deployment.getFinalArchive();
    }

    @ArquillianResource
    private JavascriptExecutor executor;

    @Test
    public void test() throws ParserConfigurationException, SAXException, IOException {
        browser.get(contextPath.toExternalForm());

        Warp.initiate(new Activity() {
            public void perform() {
                button.click();
            }
        }).inspect(new Inspection() {
            private static final long serialVersionUID = 1L;

            @BeforePhase(INVOKE_APPLICATION)
            public void publishException() {
                FacesContext context = FacesContext.getCurrentInstance();

                context.getApplication().publishEvent(context, ExceptionQueuedEvent.class,
                        new ExceptionQueuedEventContext(context, new IllegalStateException("this should be handled by JSF")));
            }
        });

        String responseText = (String) executor.executeScript("return __response");

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new ByteArrayInputStream(responseText.getBytes()));

        Element partialResponse = doc.getDocumentElement();
        assertEquals("partial-response", partialResponse.getNodeName());

        Element error = (Element) partialResponse.getElementsByTagName("error").item(0);

        String errorName = error.getElementsByTagName("error-name").item(0).getTextContent();
        String errorMessage = error.getElementsByTagName("error-message").item(0).getTextContent();

        assertEquals(IllegalStateException.class.toString(), errorName);
        assertTrue(errorMessage.contains("this should be handled by JSF"));
    }

    private static void addIndexPage(CoreDeployment deployment) {
        FaceletAsset p = new FaceletAsset();

        p.head("<h:outputScript name='jsf.js' library='javax.faces' />");
        p.head("<h:outputScript name='jquery.js' />");
        p.head("<h:outputScript name='richfaces.js' />");
        p.head("<h:outputScript>");
        p.head("    var __backup = jsf.ajax.response;");
        p.head("    var __response;");
        p.head("    jsf.ajax.response = function(request, context) {");
        p.head("        __response = request.responseText;");
        p.head("        __backup(request, context);");
        p.head("    };");
        p.head("</h:outputScript>");

        p.form("<h:panelGroup id='panel'>");
        p.form("    <h:commandButton id='button'>");
        p.form("        <f:ajax />");
        p.form("    </h:commandButton>");
        p.form("</h:panelGroup>");

        deployment.archive().addAsWebResource(p, "index.xhtml");
    }
}

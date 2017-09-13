package controllers;

import akka.http.scaladsl.model.HttpHeader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.codehaus.plexus.util.StringOutputStream;
import org.json.simple.parser.ParseException;
import play.data.Form;
import play.mvc.*;
import uk.gov.education.honours.NominationUploader;
import views.html.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This class uses a custom body parser to change the upload type.
 */
@Singleton
public class HomeController extends Controller {

    private final play.data.FormFactory formFactory;
    private final NominationUploader nominationUploader;

    @Inject
    public HomeController(play.data.FormFactory formFactory) {
        this.formFactory = formFactory;
        this.nominationUploader = new NominationUploader(System.getenv("DFE_HONS_KISSFLOWAPI"), System.getenv("DFE_HONS_FILEAPI"));
    }



    public Result login() {
        return ok(login.render(formFactory.form(Login.class)));
    }

    public Result logout() {
        session().clear();
        return redirect(routes.HomeController.login());
    }

    public Result auth() {
        Login loginForm = formFactory.form(Login.class).bindFromRequest().get();
        boolean goodCreds = Objects.equals(loginForm.user, "honoursteam") && Objects.equals(loginForm.password, System.getenv("DFE_HONS_PASSWORD"));
        if (goodCreds) {
            session().clear();
            session("email", loginForm.user);
            return redirect(routes.HomeController.index());
        } else {
            return redirect(routes.HomeController.login());
        }
    }

    @Security.Authenticated(Secured.class)
    public Result index() {
        return ok(index.render());
    }

    @Security.Authenticated(Secured.class)
    public Result nomination() {
        Form<FormData> form = formFactory.form(FormData.class);
        return ok(nomination.render(form));
    }

    @Security.Authenticated(Secured.class)
    public Result department() { return ok(department.render()); }

    @Security.Authenticated(Secured.class)
    public Result directorate() { return ok(directorate.render()); }

    @Security.Authenticated(Secured.class)
    public Result finalShortlist() { return ok(finalShortlist.render()); }

    @Security.Authenticated(Secured.class)
    public Result shortlistUploadPage() { return ok(shortlistUpload.render()); }


    @Security.Authenticated(Secured.class)
    public Result getShortlist() throws IOException, ParseException {
        String round = request().queryString().getOrDefault("round", new String[] {""})[0];
        String department = request().queryString().getOrDefault("department", new String[] {""})[0];

        response().setHeader("Content-Disposition", "attachment; filename=shortlist.xlsx");
        XSSFWorkbook xlsx = nominationUploader.getShortlist(department, round);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        xlsx.write(outputStream);
        outputStream.flush();
        return ok(outputStream.toByteArray());
    }

    @Security.Authenticated(Secured.class)
    public Result getFinalShortlist() throws IOException, ParseException {
        String round = request().queryString().get("round")[0];
        response().setHeader("Content-Disposition", "attachment; filename=shortlist.xlsx");
        XSSFWorkbook xlsx = nominationUploader.getFinalShortlist(round);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        xlsx.write(outputStream);
        outputStream.flush();
        return ok(outputStream.toByteArray());
    }

    /**
     * This method uses MyMultipartFormDataBodyParser as the body parser
     */
    @Security.Authenticated(Secured.class)
    @BodyParser.Of(MyMultipartFormDataBodyParser.class)
    public Result upload() throws Exception {
        final Http.MultipartFormData<File> formData = request().body().asMultipartFormData();
        final List<Http.MultipartFormData.FilePart<File>> fileParts = formData.getFiles();

        Pattern nominationFileNamePattern = Pattern.compile("Honours nomination web form submitted for .+\\.pdf");
        Map<String,File> fileBucket = new HashMap<>();
        for(Http.MultipartFormData.FilePart<File> filePart : fileParts) {
            fileBucket.put(filePart.getFilename(), filePart.getFile());
        }

        List<NominationUploader.Result> res = new ArrayList<>();
        for(Http.MultipartFormData.FilePart<File> filePart : fileParts) {
            if (nominationFileNamePattern.matcher(filePart.getFilename()).find()) {

                try {
                    NominationUploader.Result result = nominationUploader.uploadNomination(filePart.getFile().toPath(), filePart.getFilename(), fileBucket);
                    res.add(result);
                } catch (Exception ignored) {}
            }
        }

        return ok(success.render(res));
    }

    @Security.Authenticated(Secured.class)
    @BodyParser.Of(MyMultipartFormDataBodyParser.class)
    public Result shortlistUpload() throws Exception {
        final Http.MultipartFormData<File> formData = request().body().asMultipartFormData();
        final Http.MultipartFormData.FilePart<File> file = formData.getFile("name");
        nominationUploader.importShortlist(file.getFile());

        return ok(shortlistSuccess.render());
    }
}


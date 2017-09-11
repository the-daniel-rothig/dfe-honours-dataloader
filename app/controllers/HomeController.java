package controllers;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.parser.ParseException;
import play.data.Form;
import play.mvc.*;
import uk.gov.education.honours.NominationUploader;
import views.html.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
            return badRequest(login.render(formFactory.form(Login.class)));
        }
    }

    @Security.Authenticated(Secured.class)
    public Result index() {
        Form<FormData> form = formFactory.form(FormData.class);
        return ok(index.render(form));
    }

    @Security.Authenticated(Secured.class)
    public Result getShortlist() throws IOException, ParseException {
        String round = request().queryString().get("round")[0];
        String department = request().queryString().get("department")[0];

        response().setHeader("Content-Disposition", "attachment; filename=shortlist.xlsx");
        XSSFWorkbook xlsx = nominationUploader.getShortlist(department, round);
        FileOutputStream fileOutputStream = null;
        try {
            File file = new File("shortlist.xlsx");
            fileOutputStream = new FileOutputStream(file);
            xlsx.write(fileOutputStream);
            fileOutputStream.close();
            return ok(file);
        } finally {
            if (fileOutputStream != null) fileOutputStream.close();
        }
    }

    @Security.Authenticated(Secured.class)
    public Result getFinalShortlist() throws IOException, ParseException {
        String round = request().queryString().get("round")[0];
        response().setHeader("Content-Disposition", "attachment; filename=shortlist.xlsx");
        XSSFWorkbook xlsx = nominationUploader.getFinalShortlist(round);
        FileOutputStream fileOutputStream = null;
        try {
            File file = new File("shortlist.xlsx");
            fileOutputStream = new FileOutputStream(file);
            xlsx.write(fileOutputStream);
            return ok(file);
        } finally {
            if (fileOutputStream != null) fileOutputStream.close();
        }
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


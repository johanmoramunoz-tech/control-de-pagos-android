package cr.controles.pagos;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.speech.RecognizerIntent;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.File;
import org.json.JSONObject;

/** On-device OCR with a bundled Latin model; no network access needed. */
final class LocalOcr {
  interface Listener {void success(String proposal);void failure(String reason);}
  static void analyze(Context context, Uri uri, Listener callback) {
    final InputImage image;
    try {
      image=InputImage.fromFilePath(context,uri);
    }catch(Exception e){callback.failure("No se pudo leer la fotografía. Intenta con otra imagen.");return;}
    TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    recognizer.process(image).addOnSuccessListener(text->{
      try{callback.success(LocalReceiptParser.parseReceipt(text.getText()).toString());}
      catch(Exception e){callback.failure(e.getMessage());}
    }).addOnFailureListener(e->callback.failure("No se pudo reconocer texto localmente. Usa una foto más nítida."))
      .addOnCompleteListener(t->recognizer.close());
  }
}

package io.casehub.agency.speech;

import java.nio.file.Path;
import java.util.List;

public interface SpeakerDiarizationService {
    List<DiarizedSegment> diarize(Path audioFile, DiarizationOptions options);
}

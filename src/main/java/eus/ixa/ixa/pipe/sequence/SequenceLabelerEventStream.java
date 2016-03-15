package eus.ixa.ixa.pipe.sequence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.AdditionalContextFeatureGenerator;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

/**
 * Class for creating an event stream out of data files for training an name
 * finder.
 */
public class SequenceLabelerEventStream extends opennlp.tools.util.AbstractEventStream<SequenceSample> {

  private SequenceContextGenerator contextGenerator;

  private AdditionalContextFeatureGenerator additionalContextFeatureGenerator = new AdditionalContextFeatureGenerator();

  private String type;

  private SequenceCodec<String> codec;

  /**
   * Creates a new name finder event stream using the specified data stream and context generator.
   * @param dataStream The data stream of events.
   * @param type null or overrides the type parameter in the provided samples
   * @param contextGenerator The context generator used to generate features for the event stream.
   * @param codec the encoding
   */
  public SequenceLabelerEventStream(ObjectStream<SequenceSample> dataStream, String type, SequenceContextGenerator contextGenerator, SequenceCodec codec) {
    super(dataStream);

    this.codec = codec;

    if (codec == null) {
      this.codec = new BioCodec();
    }

    this.contextGenerator = contextGenerator;
    this.contextGenerator.addFeatureGenerator(new WindowFeatureGenerator(additionalContextFeatureGenerator, 8, 8));

    if (type != null)
      this.type = type;
    else
      this.type = "default";
  }

  public SequenceLabelerEventStream(ObjectStream<SequenceSample> dataStream) {
    this(dataStream, null, new DefaultSequenceContextGenerator(), null);
  }

  /**
   * Generates the name tag outcomes (start, continue, other) for each token in a sentence
   * with the specified length using the specified name spans.
   * @param names Token spans for each of the names.
   * @param type null or overrides the type parameter in the provided samples
   * @param length The length of the sentence.
   * @return An array of start, continue, other outcomes based on the specified names and sentence length.
   *
   * @deprecated use the BioCodec implementation of the SequenceValidator instead!
   */
  @Deprecated
  public static String[] generateOutcomes(Span[] names, String type, int length) {
    String[] outcomes = new String[length];
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = SequenceLabelerME.OTHER;
    }
    for (Span name : names) {
      if (name.getType() == null) {
        outcomes[name.getStart()] = type + "-" + SequenceLabelerME.START;
      }
      else {
        outcomes[name.getStart()] = name.getType() + "-" + SequenceLabelerME.START;
      }
      // now iterate from begin + 1 till end
      for (int i = name.getStart() + 1; i < name.getEnd(); i++) {
        if (name.getType() == null) {
          outcomes[i] = type + "-" + SequenceLabelerME.CONTINUE;
        }
        else {
          outcomes[i] = name.getType() + "-" + SequenceLabelerME.CONTINUE;
        }
      }
    }
    return outcomes;
  }

  public static List<Event> generateEvents(String[] sentence, String[] outcomes, SequenceContextGenerator cg) {
    List<Event> events = new ArrayList<Event>(outcomes.length);
    for (int i = 0; i < outcomes.length; i++) {
      events.add(new Event(outcomes[i], cg.getContext(i, sentence, outcomes,null)));
    }

    cg.updateAdaptiveData(sentence, outcomes);

    return events;
  }

  @Override
  protected Iterator<Event> createEvents(SequenceSample sample) {

    if (sample.isClearAdaptiveDataSet()) {
      contextGenerator.clearAdaptiveData();
    }

    String outcomes[] = codec.encode(sample.getSequences(), sample.getTokens().length);
//    String outcomes[] = generateOutcomes(sample.getNames(), type, sample.getSentence().length);
    additionalContextFeatureGenerator.setCurrentContext(sample.getAdditionalContext());
    String[] tokens = new String[sample.getTokens().length];

    for (int i = 0; i < sample.getTokens().length; i++) {
      tokens[i] = sample.getTokens()[i];
    }

    return generateEvents(tokens, outcomes, contextGenerator).iterator();
  }


  /**
   * Generated previous decision features for each token based on contents of the specified map.
   * @param tokens The token for which the context is generated.
   * @param prevMap A mapping of tokens to their previous decisions.
   * @return An additional context array with features for each token.
   */
  public static String[][] additionalContext(String[] tokens, Map<String, String> prevMap) {
    String[][] ac = new String[tokens.length][1];
    for (int ti=0;ti<tokens.length;ti++) {
      String pt = prevMap.get(tokens[ti]);
      ac[ti][0]="pd="+pt;
    }
    return ac;

  }
}


package eus.ixa.ixa.pipe.sequence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AggregatedFeatureGenerator;
import opennlp.tools.util.featuregen.FeatureGeneratorResourceProvider;
import opennlp.tools.util.featuregen.GeneratorFactory;

// Idea of this factory is that most resources/impls used by the name finder
// can be modified through this class!
// That only works if thats the central class used for training/runtime

public class SequenceLabelerFactory extends BaseToolFactory {

  private byte[] featureGeneratorBytes;
  private Map<String, Object> resources;
  private SequenceCodec<String> seqCodec;

  /**
   * Creates a {@link SequenceLabelerFactory} that provides the default implementation
   * of the resources.
   */
  public SequenceLabelerFactory() {
    this.seqCodec = new BioCodec();
  }

  public SequenceLabelerFactory(byte[] featureGeneratorBytes, final Map<String, Object> resources,
      SequenceCodec<String> seqCodec) {
    init(featureGeneratorBytes, resources, seqCodec);
  }

  void init(byte[] featureGeneratorBytes, final Map<String, Object> resources, SequenceCodec<String> seqCodec) {
    this.featureGeneratorBytes = featureGeneratorBytes;
    this.resources = resources;
    this.seqCodec = seqCodec;
  }

  private static byte[] loadDefaultFeatureGeneratorBytes() {
    
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = SequenceLabelerFactory.class.getResourceAsStream(
        "/opennlp/tools/namefind/ner-default-features.xml")) {
      
      if (in == null) {
        throw new IllegalStateException("Classpath must contain ner-default-features.xml file!");
      }
      
      byte buf[] = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        bytes.write(buf, 0, len);
      }
    }
    catch (IOException e) {
      throw new IllegalStateException("Failed reading from ner-default-features.xml file on classpath!");
    }
    
    return bytes.toByteArray();
  }
  
  protected SequenceCodec<String> getSequenceCodec() {
    return seqCodec;
  }

  protected Map<String, Object> getResources() {
    return resources;
  }

  protected byte[] getFeatureGenerator() {
    return featureGeneratorBytes;
  }

  public static SequenceLabelerFactory create(String subclassName, byte[] featureGeneratorBytes, final Map<String, Object> resources,
      SequenceCodec<String> seqCodec)
      throws InvalidFormatException {
    SequenceLabelerFactory theFactory;
    if (subclassName == null) {
      // will create the default factory
      theFactory = new SequenceLabelerFactory();
    } else {
      try {
        theFactory = ExtensionLoader.instantiateExtension(
            SequenceLabelerFactory.class, subclassName);
      } catch (Exception e) {
        String msg = "Could not instantiate the " + subclassName
            + ". The initialization throw an exception.";
        System.err.println(msg);
        e.printStackTrace();
        throw new InvalidFormatException(msg, e);
      }
    }
    theFactory.init(featureGeneratorBytes, resources, seqCodec);
    return theFactory;
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // no additional artifacts
  }

  public SequenceCodec<String> createSequenceCodec() {

    if (artifactProvider != null) {
      String sequeceCodecImplName = artifactProvider.getManifestProperty(
          SequenceLabelerModel.SEQUENCE_CODEC_CLASS_NAME_PARAMETER);
      return instantiateSequenceCodec(sequeceCodecImplName);
    }
    else {
      return seqCodec;
    }
  }

  public SequenceContextGenerator createContextGenerator() {

    AdaptiveFeatureGenerator featureGenerator = createFeatureGenerators();

    if (featureGenerator == null) {
      featureGenerator = SequenceLabelerME.createFeatureGenerator();
    }

    return new DefaultSequenceContextGenerator(featureGenerator);
  }

  /**
   * Creates the {@link AdaptiveFeatureGenerator}. Usually this
   * is a set of generators contained in the {@link AggregatedFeatureGenerator}.
   *
   * Note:
   * The generators are created on every call to this method.
   *
   * @return the feature generator or null if there is no descriptor in the model
   */
  public AdaptiveFeatureGenerator createFeatureGenerators() {

    if (featureGeneratorBytes == null && artifactProvider != null) {
      featureGeneratorBytes = (byte[]) artifactProvider.getArtifact(
          SequenceLabelerModel.GENERATOR_DESCRIPTOR_ENTRY_NAME);
    }
    
    if (featureGeneratorBytes == null) {
      featureGeneratorBytes = loadDefaultFeatureGeneratorBytes();
    }

    InputStream descriptorIn = new ByteArrayInputStream(featureGeneratorBytes);

    AdaptiveFeatureGenerator generator = null;
    try {
      generator = GeneratorFactory.create(descriptorIn, new FeatureGeneratorResourceProvider() {

        public Object getResource(String key) {
          if (artifactProvider != null) {
            return artifactProvider.getArtifact(key);
          }
          else {
            return resources.get(key);
          }
        }
      });
    } catch (InvalidFormatException e) {
      // It is assumed that the creation of the feature generation does not
      // fail after it succeeded once during model loading.

      // But it might still be possible that such an exception is thrown,
      // in this case the caller should not be forced to handle the exception
      // and a Runtime Exception is thrown instead.

      // If the re-creation of the feature generation fails it is assumed
      // that this can only be caused by a programming mistake and therefore
      // throwing a Runtime Exception is reasonable

      throw new SequenceLabelerModel.FeatureGeneratorCreationError(e);
    } catch (IOException e) {
      throw new IllegalStateException("Reading from mem cannot result in an I/O error", e);
    }

    return generator;
  }

  public static SequenceCodec<String> instantiateSequenceCodec(
      String sequenceCodecImplName) {

    if (sequenceCodecImplName != null) {
      return ExtensionLoader.instantiateExtension(
          SequenceCodec.class, sequenceCodecImplName);
    }
    else {
      // If nothing is specified return old default!
      return new BioCodec();
    }
  }
}



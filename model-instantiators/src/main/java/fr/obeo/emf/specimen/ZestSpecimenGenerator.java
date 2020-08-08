/*******************************************************************************
 * Copyright (c) 2012 Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Obeo - initial API and implementation
 *     Abel G�mez (AtlanMod) - Additional modifications  
 *     Hoang Lam Nguyen - Integration into JQF-Zest    
 *******************************************************************************/

package fr.obeo.emf.specimen;

import static com.google.common.collect.Iterables.get;
import static com.google.common.primitives.Primitives.isWrapperType;
import static com.google.common.primitives.Primitives.unwrap;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.math3.distribution.IntegerDistribution;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import fr.inria.atlanmod.instantiator.ZestMetamodelConfig;
import fr.obeo.emf.specimen.internal.EPackagesData;

/**
 * @author <a href="mailto:mikael.barbero@obeo.fr">Mikael Barbero</a>
 * @author <a href="mailto:abel.gomez-llana@inria.fr">Abel G�mez</a>
 */
public class ZestSpecimenGenerator {

	public final static Logger LOGGER = Logger.getLogger(SpecimenGenerator.class.getName());

	protected SourceOfRandomness randomGenerator;
	protected final ZestMetamodelConfig configuration;
	protected final EPackagesData ePackagesData;

	/* inner Variable state */
	protected int currentDepth;
	protected int currentMaxDepth;
	protected int currentObjects;
	protected int goalObjects;

	public ZestSpecimenGenerator(ZestMetamodelConfig configuration) {
		this.configuration = configuration;
		ePackagesData = new EPackagesData(configuration.ePackages(), configuration.ignoredEClasses());
	}
	
	// Lam: modified to create model from root element Model
	public void generate(Resource resource, SourceOfRandomness random, EClass root) {
		this.randomGenerator = random;
		
		resource.setModified(true);
		
		ListMultimap<EClass, EObject> indexByKind = ArrayListMultimap.create();

		ImmutableSet<EClass> possibleRootEClasses = configuration.possibleRootEClasses();

		currentDepth = 0;
		currentMaxDepth = 0;
		currentObjects = 0;
		goalObjects = configuration.getResourceSizeDistribution(random);

		// loop for creating root elements 
		//while (currentObjects < goalObjects) {
		EClass rootClass = root;
		//if (dist == null) continue;
		currentMaxDepth = configuration.getDepthDistributionFor(rootClass, random);
		if (currentMaxDepth == 0) currentMaxDepth++;
		Optional<EObject> generateEObject = generateEObject(rootClass, indexByKind, random);
		if (generateEObject.isPresent()) {
			resource.getContents().add(generateEObject.get());
		}
			
		//}

		LOGGER.info("Generating cross-references");

		int totalEObjects = currentObjects;
		int currentEObject = 0;
		TreeIterator<EObject> eAllContents = resource.getAllContents();
		while (eAllContents.hasNext()) {
			currentEObject++;
			LOGGER.fine(MessageFormat.format("Generating cross references {0} / {1}", currentEObject, totalEObjects));
			EObject eObject = eAllContents.next();
			generateCrossReferences(eObject, indexByKind, random);
		}

		LOGGER.info(MessageFormat.format("Requested #EObject={0}", goalObjects));
		
		LOGGER.info(MessageFormat.format("Actual #EObject={0}", ImmutableSet.copyOf(indexByKind.values()).size()));

		for (Map.Entry<EClass, Collection<EObject>> entry : indexByKind.asMap().entrySet()) {
			// Log number of elements for resolved EClasses
			EClass eClass = entry.getKey();
			if (!eClass.eIsProxy() || (eClass.eIsProxy() && EcoreUtil.resolve(eClass, resource) != eClass)) {
				LOGGER.info(MessageFormat.format("#{0}::{1}={2}", 
						eClass.getEPackage().getNsURI(),
						eClass.getName(),
						entry.getValue().size()));
			}
		}
		for (Map.Entry<EClass, Collection<EObject>> entry : indexByKind.asMap().entrySet()) {
			EClass eClass = entry.getKey();
			if (eClass.eIsProxy() && EcoreUtil.resolve(eClass, resource) == eClass) {
				// Warn about unresolved EClasses
				LOGGER.warning(MessageFormat.format("#{0} (unresolved)={1}", 
						EcoreUtil.getURI(eClass),
						entry.getValue().size()));
			}
		}

		LOGGER.info(MessageFormat.format("Generation finished for resource ''{0}''", resource.getURI()));
	}

	/**
	 * @param eObject
	 * @param indexByKind
	 */
	protected void generateCrossReferences(EObject eObject, ListMultimap<EClass, EObject> indexByKind, SourceOfRandomness random) {
		Iterable<EReference> eAllNonContainment = ePackagesData.eAllNonContainment(eObject.eClass());
		for (EReference eReference : eAllNonContainment) {
			EClass eReferenceType = eReference.getEReferenceType();

			if (eReference.isMany()) {
				@SuppressWarnings("unchecked")
				List<Object> values = (List<Object>) eObject.eGet(eReference);
				int sample = configuration.getDistributionFor(eReference, random);
				LOGGER.fine(MessageFormat.format("Generating {0} values for EReference ''{1}'' in EObject {2}", sample, eReference.getName(), eObject.toString()));
				for (int i = 0; i < sample; i++) {
					List<EObject> possibleValues = indexByKind.get(eReferenceType);
					if (!possibleValues.isEmpty()) {
						final EObject nextEObject = possibleValues.get(randomGenerator.nextInt(possibleValues.size()));
						values.add(nextEObject);
					}
				}
			} else {
				if (eReference.isRequired() || random.nextBoolean()) {
					LOGGER.fine(MessageFormat.format("Generating EReference ''{0}'' in EObject {1}", eReference.getName(), eObject.toString()));
					List<EObject> possibleValues = indexByKind.get(eReferenceType);
					if (!possibleValues.isEmpty()) {
						final EObject nextEObject = possibleValues.get(randomGenerator.nextInt(possibleValues.size()));
						eObject.eSet(eReference, nextEObject);
					}
				}
			}
		}
	}

	protected Optional<EObject> generateEObject(EClass eClass, ListMultimap<EClass, EObject> indexByKind,
			SourceOfRandomness random) {
		final EObject eObject;
		currentObjects++;
		LOGGER.fine(MessageFormat.format("Generating EObject {0} / ~{1} (EClass={2})", 
				currentObjects, goalObjects, eClass.getName()));
		eObject = createEObject(eClass, indexByKind);
		generateEAttributes(eObject, eClass, random);
		generateEContainmentReferences(eObject, eClass, indexByKind, random);
		return Optional.fromNullable(eObject);
	}

	protected EObject createEObject(EClass eClass, ListMultimap<EClass, EObject> indexByKind) {
		EObject eObject = eClass.getEPackage().getEFactoryInstance().create(eClass);

		indexByKind.put(eClass, eObject);
		for (EClass eSuperType : eClass.getEAllSuperTypes()) {
			indexByKind.put(eSuperType, eObject);
		}

		return eObject;
	}

	/**
	 * @param eObject
	 * @param eClass
	 * @param indexByKind
	 */
	protected void generateEContainmentReferences(EObject eObject, EClass eClass,
			ListMultimap<EClass, EObject> indexByKind, SourceOfRandomness random) {
		
		ImmutableList<EReference> allEReferences = (ImmutableList<EReference>) ePackagesData.eAllContainment(eClass);	
		for(EReference eReference : allEReferences) {
			if (eReference.isRequired()) {
				generateEContainmentReference(eObject, eReference, indexByKind, random);
			}
		}
		
		if(allEReferences.isEmpty()) {
			return;
		}
		
		int breadth = configuration.getBreadthDistributionFor(eClass, random);
		for(int i = 0; i < breadth; i++) {
			if((currentObjects < goalObjects && currentDepth <= currentMaxDepth)) {
				int idx = random.nextInt(allEReferences.size());
				generateEContainmentReference(eObject, allEReferences.get(idx), indexByKind, random);
			}	
		}
	}

	/**
	 * @param eObject
	 * @param eReference
	 * @param indexByKind
	 */
	protected void generateEContainmentReference(EObject eObject, EReference eReference,
			ListMultimap<EClass, EObject> indexByKind, SourceOfRandomness random) {
		currentDepth++;

		ImmutableList<EClass> eAllConcreteSubTypeOrSelf = ePackagesData.eAllConcreteSubTypeOrSelf(eReference);
		ImmutableMultiset<EClass> eAllConcreteSubTypesOrSelf = getEReferenceTypesWithWeight(eReference,
				eAllConcreteSubTypeOrSelf);

		if (!eAllConcreteSubTypesOrSelf.isEmpty()) {
			if (eReference.isMany()) {
				generateManyContainmentReference(eObject, eReference, indexByKind, eAllConcreteSubTypesOrSelf, random);
			} else {
				generateSingleContainmentReference(eObject, eReference, indexByKind, eAllConcreteSubTypesOrSelf, random);
			}
		}

		currentDepth--;
	}

	protected void generateSingleContainmentReference(EObject eObject, EReference eReference,
			ListMultimap<EClass, EObject> indexByKind, ImmutableMultiset<EClass> eAllConcreteSubTypesOrSelf,
			SourceOfRandomness random) {
		if (eReference.isRequired()) {
			LOGGER.fine(MessageFormat.format("Generating EReference ''{0}'' in EObject {1}", eReference.getName(), eObject.toString()));
			int idx = random.nextInt(eAllConcreteSubTypesOrSelf.size());
			final Optional<EObject> nextEObject = generateEObject(get(eAllConcreteSubTypesOrSelf, idx), indexByKind, random);
			if (nextEObject.isPresent()) {
				eObject.eSet(eReference, nextEObject.get());
			}
		}
	}

	protected void generateManyContainmentReference(EObject eObject, EReference eReference,
			ListMultimap<EClass, EObject> indexByKind, ImmutableMultiset<EClass> eAllConcreteSubTypesOrSelf,
			SourceOfRandomness random) {
		@SuppressWarnings("unchecked")
		List<EObject> values = (List<EObject>) eObject.eGet(eReference);
		int sample = configuration.getDistributionFor(eReference, random)+1;
		LOGGER.fine(MessageFormat.format("Generating {0} values for EReference ''{1}'' in EObject {2}", sample, eReference.getName(), eObject.toString()));
		for (int i = 0; i < sample; i++) {
			int idx = randomGenerator.nextInt(eAllConcreteSubTypesOrSelf.size());
			final Optional<EObject> nextEObject = generateEObject(get(eAllConcreteSubTypesOrSelf, idx), indexByKind, random);
			if (nextEObject.isPresent()) {
				values.add(nextEObject.get());
			}
		}
	}

	protected ImmutableMultiset<EClass> getEReferenceTypesWithWeight(EReference eReference,
			ImmutableList<EClass> eAllSubTypesOrSelf) {
		ImmutableMultiset.Builder<EClass> eAllSubTypesOrSelfWithWeights = ImmutableMultiset.builder();
		for (EClass eClass : eAllSubTypesOrSelf) {
			eAllSubTypesOrSelfWithWeights.addCopies(eClass, configuration.getWeightFor(eReference, eClass));
		}
		return eAllSubTypesOrSelfWithWeights.build();
	}

	/**
	 * @param eObject
	 * @param eClass
	 */
	protected void generateEAttributes(EObject eObject, EClass eClass, SourceOfRandomness random) {
		for (EAttribute eAttribute : ePackagesData.eAllAttributes(eClass)) {
			generateAttributes(eObject, eAttribute, random);
		}
	}

	protected void generateAttributes(EObject eObject, EAttribute eAttribute, SourceOfRandomness random) {
		EDataType eAttributeType = eAttribute.getEAttributeType();
		Class<?> instanceClass = eAttributeType.getInstanceClass();
		if (eAttribute.isMany()) {
			generateManyAttribute(eObject, eAttribute, random, instanceClass);
		} else {
			generateSingleAttribute(eObject, eAttribute, random, instanceClass);
		}
	}

	protected void generateSingleAttribute(EObject eObject, EAttribute eAttribute, SourceOfRandomness random,
			Class<?> instanceClass) {
		if (eAttribute.isRequired() || random.nextBoolean()) {
			final Object value;
			EDataType eAttributeType = eAttribute.getEAttributeType();
			if (eAttributeType instanceof EEnum) {
				EEnum eEnum = (EEnum) eAttributeType;
				int size = eEnum.getELiterals().size();
				if (instanceClass == null) {
					// Initial implementation TODO: review
					instanceClass = int.class;
					int randomValue = Math.abs((Integer) nextValue(instanceClass, random));
					value = eEnum.getELiterals().get(randomValue % size);
				} else {
					// Fix UML EEnumLiteral 
					int idx = randomGenerator.nextInt(size);
					value = eEnum.getELiterals().get(idx).getInstance();
				}
			} else {
				value = nextValue(instanceClass, random);
			}
			eObject.eSet(eAttribute, value);
		}
	}

	protected void generateManyAttribute(EObject eObject, EAttribute eAttribute, SourceOfRandomness random,
			Class<?> instanceClass) {
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) eObject.eGet(eAttribute);
		int sample = configuration.getDistributionFor(eAttribute, random);
		for (int i = eAttribute.getLowerBound(); i < sample; i++) {
			final Object value;
			EDataType eAttributeType = eAttribute.getEAttributeType();
			if (eAttributeType instanceof EEnum) {
				assert instanceClass == null;
				EEnum eEnum = (EEnum) eAttributeType;
				instanceClass = int.class;
				int randomValue = Math.abs((Integer) nextValue(instanceClass, random));
				int size = eEnum.getELiterals().size();
				value = eEnum.getELiterals().get(randomValue % size); 
			} else {
				value = nextValue(instanceClass, random);
			}
			values.add(value);
		}
	}

	protected Object nextValue(Class<?> instanceClass, SourceOfRandomness random) {
		final Object value;
		if (instanceClass.isPrimitive() || isWrapperType(instanceClass)) {
			value = nextPrimitive(unwrap(instanceClass));
		} else {
			value = nextObject(instanceClass, random);
		}
		return value;
	}

	/**
	 * @param instanceClass
	 */
	protected Object nextObject(Class<?> instanceClass, SourceOfRandomness random) {
		if (instanceClass == String.class) {
			return RandomStringUtils.random(
					configuration.getValueDistributionFor(instanceClass, random), 
					0, 0, true, true, null, random.toJDKRandom());
		} else {
			LOGGER.warning(
					MessageFormat.format("Do not know how to randomly generate ''{0}'' object",
					instanceClass.getName()));
		}
		return null;
	}

	/**
	 * @param eObject
	 * @param eAttribute
	 * @param instanceClass
	 */
	protected Object nextPrimitive(Class<?> instanceClass) {
		if (instanceClass == boolean.class) {
			return randomGenerator.nextBoolean();
		} else if (instanceClass == byte.class) {
			byte[] buff = new byte[1];
			randomGenerator.nextBytes(buff);
			return buff[0];
		} else if (instanceClass == char.class) {
			char nextChar = (char) randomGenerator.nextInt();
			return nextChar;
		} else if (instanceClass == double.class) {
			return randomGenerator.nextDouble();
		} else if (instanceClass == float.class) {
			return randomGenerator.nextFloat();
		} else if (instanceClass == int.class) {
			return randomGenerator.nextInt();
		} else if (instanceClass == long.class) {
			return randomGenerator.nextLong();
		} else if (instanceClass == short.class) {
			short nextShort = (short) randomGenerator.nextInt();
			return nextShort;
		} else {
			throw new IllegalArgumentException();
		}
	}

	protected boolean booleanInDistribution(IntegerDistribution distribution) {
		int sample = distribution.sample();
		return sample <= distribution.getNumericalMean();
	}
}
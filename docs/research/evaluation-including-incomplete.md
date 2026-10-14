# Same evaluation including incomplete observations

This report evaluates what amethyst growth-stage observations can and cannot tell about how long a geode's chunk was randomly ticked, in a controlled test world with known ground truth. It does not identify players or locate bases, and a correlation found here does not establish causation outside the tested setup.

## Setup

- Sweep geodes: 40 (feature seeds 2001-2040)
- Geode center: -9385840, 42, -1586565
- Minecraft: 1.21.11
- World seed: 0
- Method: vanilla minecraft:amethyst_geode feature in a stone cube, growth by calling BlockState.randomTick on every budding block with a seeded RandomSource; observations by the analyzer's grouping code on server block data
- Harvest arm: 12 geodes: 1300 random ticks, then 70% of clusters removed, then 26 more random ticks
- Exposure levels (random ticks): [0, 10, 20, 35, 52, 75, 100, 130, 180, 300, 520, 800, 1300, 2000]
- Samples: 666 observations; 666 used, 0 incomplete observations excluded
- Split by geode: 35 training geodes, 17 held-out test geodes (every third geode by id). Repeated observations of one geode never span both sets.
- Exposure unit: random ticks received by each budding block. One hour of a loaded, randomly ticking chunk at the default random tick speed (3) is 52.7 random ticks.
- Classifiers receive only observed features (stage counts, budding blocks, face occupancy). Ground truth is stored separately and is used only to score predictions.
- Pre-registered support criteria (held-out set): balanced accuracy >= 0.80, false-positive and false-negative rates <= 0.20, at least 30 test samples and 10 per class. Below 0.65 balanced accuracy a hypothesis is unsupported.

## Results by hypothesis

| Hypothesis | Verdict | Test samples (pos/neg) | Threshold | Precision | Recall | False-positive rate | False-negative rate |
|---|---|---|---|---|---|---|---|
| H1 Cluster share separates short from long exposure | **SUPPORTED** | 197 (105/92) | 0.611 | 97.1% | 96.2% | 3.3% | 3.8% |
| H1b Cluster share among large buds and clusters separates short from long exposure | **SUPPORTED** | 194 (104/90) | 0.862 | 92.6% | 84.6% | 7.8% | 15.4% |
| H2 Cluster share distinguishes durations beyond saturation | **UNSUPPORTED** | 77 (38/39) | 0.188 | 49.4% | 100.0% | 100.0% | 0.0% |
| H3 Any cluster indicates exposure (naive rule) | **UNSUPPORTED** | 212 (164/48) | 1.000 | 81.7% | 95.1% | 72.9% | 4.9% |

### H1: Cluster share separates short from long exposure

Statement: Geodes whose budding blocks were randomly ticked for at least 2 hours (at default speed) show a higher share of clusters among all growth objects than geodes with less exposure, enough to classify them on unseen geodes.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.96, false-positive rate 0.03, false-negative rate 0.04).
- Training: 408 samples; held-out test: 197 samples
- Test precision: 97.1% (101/104; 95% CI 91.9-99.0%)
- Test recall: 96.2% (101/105; 95% CI 90.6-98.5%)
- Test false-positive rate: 3.3% (3/92; 95% CI 1.1-9.2%)
- Test false-negative rate: 3.8% (4/105; 95% CI 1.5-9.4%)
- Balanced accuracy: test 0.965, training 0.956 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-7.61 + 12.51 x))) fitted on training geodes: Brier score 0.037, expected calibration error 0.038 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 82 | 2.0% | 3.7% |
| 0.2-0.4 | 8 | 29.0% | 12.5% |
| 0.4-0.6 | 6 | 51.2% | 16.7% |
| 0.6-0.8 | 5 | 70.3% | 100.0% |
| 0.8-1.0 | 96 | 97.6% | 99.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 98.9% | 2.2% | 0.0% |
| partial-scan | 3 | 2 | 33.3% | 100.0% | 50.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 25.0% | n/a | 75.0% |

### H1b: Cluster share among large buds and clusters separates short from long exposure

Statement: Same as H1 but using clusters / (large buds + clusters), which ignores small and medium buds.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / (large + clusters) >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.88, false-positive rate 0.08, false-negative rate 0.15).
- Training: 408 samples; held-out test: 194 samples
- Test precision: 92.6% (88/95; 95% CI 85.6-96.4%)
- Test recall: 84.6% (88/104; 95% CI 76.5-90.3%)
- Test false-positive rate: 7.8% (7/90; 95% CI 3.8-15.2%)
- Test false-negative rate: 15.4% (16/104; 95% CI 9.7-23.5%)
- Balanced accuracy: test 0.884, training 0.891 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-13.03 + 15.64 x))) fitted on training geodes: Brier score 0.071, expected calibration error 0.044 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 69 | 3.6% | 0.0% |
| 0.2-0.4 | 18 | 30.5% | 33.3% |
| 0.4-0.6 | 10 | 51.1% | 80.0% |
| 0.6-0.8 | 10 | 68.7% | 90.0% |
| 0.8-1.0 | 87 | 92.5% | 93.1% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 180 | 91 | 88.9% | 6.7% | 15.4% |
| partial-scan | 2 | 1 | 50.0% | 100.0% | 0.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 50.0% | n/a | 50.0% |

### H2: Cluster share distinguishes durations beyond saturation

Statement: Among geodes exposed for at least 5 hours, those exposed for at least 24 hours have a higher cluster share.

- Label: positive when exposure >= 1265.6 random ticks (24.0 h); only samples with exposure >= 263.7 random ticks (5.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.50, false-positive rate 1.00, false-negative rate 0.00).
- Training: 159 samples; held-out test: 77 samples
- Test precision: 49.4% (38/77; 95% CI 38.5-60.3%)
- Test recall: 100.0% (38/38; 95% CI 90.8-100.0%)
- Test false-positive rate: 100.0% (39/39; 95% CI 91.0-100.0%)
- Test false-negative rate: 0.0% (0/38; 95% CI 0.0-9.2%)
- Balanced accuracy: test 0.500, training 0.500 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(6.20 + -6.35 x))) fitted on training geodes: Brier score 0.238, expected calibration error 0.005 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 0 | - | - |
| 0.4-0.6 | 73 | 46.4% | 46.6% |
| 0.6-0.8 | 1 | 77.0% | 100.0% |
| 0.8-1.0 | 3 | 97.3% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 65 | 26 | 40.0% | 100.0% | 0.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

### H3: Any cluster indicates exposure (naive rule)

Statement: A geode containing at least one fully grown cluster has been exposed for at least 30 minutes. Threshold fixed in advance at 'one or more clusters'; not fitted.

- Label: positive when exposure >= 26.4 random ticks (0.5 h)
- Rule: cluster count >= threshold, threshold fixed in advance
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.61, false-positive rate 0.73, false-negative rate 0.05).
- Training: 454 samples; held-out test: 212 samples
- Test precision: 81.7% (156/191; 95% CI 75.6-86.5%)
- Test recall: 95.1% (156/164; 95% CI 90.7-97.5%)
- Test false-positive rate: 72.9% (35/48; 95% CI 59.0-83.4%)
- Test false-negative rate: 4.9% (8/164; 95% CI 2.5-9.3%)
- Balanced accuracy: test 0.611, training 0.632 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-1.39 + 0.40 x))) fitted on training geodes: Brier score 0.084, expected calibration error 0.060 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 21 | 19.9% | 38.1% |
| 0.2-0.4 | 18 | 30.8% | 16.7% |
| 0.4-0.6 | 18 | 50.5% | 22.2% |
| 0.6-0.8 | 16 | 70.5% | 68.8% |
| 0.8-1.0 | 139 | 98.6% | 99.3% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 143 | 81.3% | 87.2% | 0.0% |
| partial-scan | 18 | 9 | 50.0% | 11.1% | 88.9% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

## Cluster share versus exposure (sweep arm, complete observations)

| Random ticks | Hours at default speed | Samples | Mean cluster share | SD | Min | Max |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0 | 40 | 22.8% | 0.176 | 0.0% | 60.0% |
| 10 | 0.2 | 40 | 17.3% | 0.102 | 0.0% | 40.0% |
| 20 | 0.4 | 40 | 16.9% | 0.094 | 0.0% | 36.4% |
| 35 | 0.7 | 40 | 20.5% | 0.083 | 5.9% | 42.9% |
| 52 | 1.0 | 40 | 27.7% | 0.075 | 10.5% | 47.1% |
| 75 | 1.4 | 40 | 37.8% | 0.074 | 23.3% | 53.3% |
| 100 | 1.9 | 40 | 55.0% | 0.091 | 32.3% | 76.5% |
| 130 | 2.5 | 40 | 71.8% | 0.091 | 48.5% | 92.7% |
| 180 | 3.4 | 40 | 86.8% | 0.069 | 66.7% | 100.0% |
| 300 | 5.7 | 40 | 99.2% | 0.016 | 93.9% | 100.0% |
| 520 | 9.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 800 | 15.2 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 1300 | 24.7 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 2000 | 37.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |

Repeated observations: in 40 of 40 sweep geodes the cluster count never decreased between consecutive observations.

## Findings derived from the data

- The mean cluster share is not monotone in exposure: it fell from 22.8% at 0 to 17.3% at 10 random ticks. New small buds on free faces add to the denominator before any of them matures, so a higher share does not simply mean longer exposure.
- The share saturates: from 300 random ticks (about 5.7 h at default speed) the mean is 99.2%, so longer exposure leaves no further trace in this statistic.
- Freshly generated geodes already have a mean cluster share of 22.8% (range 0.0-60.0%) from world generation alone.
- H1: in the 'partial-scan' condition 1 of 2 long-exposure geodes were missed (held-out set; small sample).
- H1: in the 'harvested-regrowth' condition 3 of 4 long-exposure geodes were missed (held-out set; small sample).
- H1b: in the 'harvested-regrowth' condition 2 of 4 long-exposure geodes were missed (held-out set; small sample).
- H3: in the 'partial-scan' condition 8 of 9 long-exposure geodes were missed (held-out set; small sample).
- 40 of 40 repeatedly observed geodes never lost a cluster between observations (expected: growth never reverses without block removal).

## Limitations and confounders

- Precision depends on the share of long-exposure geodes in the sample, which here is set by the experiment design. Applied to a population with a different mix, the same rule would have a different precision.
- Exposure was produced by calling the game's real random-tick code on budding blocks. In a live world, random ticks reach every entity-ticking chunk (including force-loaded ones), while a chunk's inhabited time only counts nearby non-spectator players; the two are therefore not interchangeable.
- The random tick speed game rule scales growth directly; a world with a different setting shifts every threshold.
- Generation already places buds of all four stages (about a quarter clusters on average), so fresh geodes are not cluster-free.
- Growth saturates: once every free face holds a cluster, more time leaves no trace.
- Harvesting clusters resets the visible state; harvested geodes look younger than they are.
- Small geodes have few growth objects, so their shares are noisy; incomplete observations undercount.
- Geode fixtures share one generation method (vanilla feature, flat test world seed); natural terrain, caves and water can change free faces.
- Geode shapes depend on the world seed and position as well as the feature seed, so a rerun in another world produces a different (equally valid) sample; the world seed and position are listed under Setup.
- Results hold only for the tested environment and the stated exposure range.

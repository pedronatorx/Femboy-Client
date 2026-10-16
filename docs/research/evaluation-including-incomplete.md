# Same evaluation including incomplete observations

This report evaluates what amethyst growth-stage observations can and cannot tell about how long a geode's chunk was randomly ticked, in a controlled test world with known ground truth. It does not identify players or locate bases, and a correlation found here does not establish causation outside the tested setup.

## Setup

- Harvest arm: 12 geodes: 1300 random ticks, then 70% of clusters removed, then 26 more random ticks
- Method: vanilla minecraft:amethyst_geode feature in a stone cube, growth by calling BlockState.randomTick on every budding block with a seeded RandomSource; observations by the analyzer's grouping code on server block data
- World seed: 0
- Minecraft: 1.21.11
- Geode center: -11612336, 42, 521096
- Sweep geodes: 40 (feature seeds 2001-2040)
- Exposure levels (random ticks): [0, 10, 20, 35, 52, 75, 100, 130, 180, 300, 520, 800, 1300, 2000]
- Samples: 610 observations; 610 used, 0 incomplete observations excluded
- Split by geode: 35 training geodes, 17 held-out test geodes (every third geode by id). Repeated observations of one geode never span both sets.
- Exposure unit: random ticks received by each budding block. One hour of a loaded, randomly ticking chunk at the default random tick speed (3) is 52.7 random ticks.
- Classifiers receive only observed features (stage counts, budding blocks, face occupancy). Ground truth is stored separately and is used only to score predictions.
- Pre-registered support criteria (held-out set): balanced accuracy >= 0.80, false-positive and false-negative rates <= 0.20, at least 30 test samples and 10 per class. Below 0.65 balanced accuracy a hypothesis is unsupported.

## Results by hypothesis

| Hypothesis | Verdict | Test samples (pos/neg) | Threshold | Precision | Recall | False-positive rate | False-negative rate |
|---|---|---|---|---|---|---|---|
| H1 Cluster share separates short from long exposure | **SUPPORTED** | 194 (103/91) | 0.633 | 99.0% | 93.2% | 1.1% | 6.8% |
| H1b Cluster share among large buds and clusters separates short from long exposure | **SUPPORTED** | 192 (103/89) | 0.837 | 94.1% | 92.2% | 6.7% | 7.8% |
| H2 Cluster share distinguishes durations beyond saturation | **UNSUPPORTED** | 77 (38/39) | 0.217 | 49.4% | 100.0% | 100.0% | 0.0% |
| H3 Any cluster indicates exposure (naive rule) | **UNSUPPORTED** | 196 (156/40) | 1.000 | 81.1% | 98.7% | 90.0% | 1.3% |

### H1: Cluster share separates short from long exposure

Statement: Geodes whose budding blocks were randomly ticked for at least 2 hours (at default speed) show a higher share of clusters among all growth objects than geodes with less exposure, enough to classify them on unseen geodes.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.96, false-positive rate 0.01, false-negative rate 0.07).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 99.0% (96/97; 95% CI 94.4-99.8%)
- Test recall: 93.2% (96/103; 95% CI 86.6-96.7%)
- Test false-positive rate: 1.1% (1/91; 95% CI 0.2-6.0%)
- Test false-negative rate: 6.8% (7/103; 95% CI 3.3-13.4%)
- Balanced accuracy: test 0.961, training 0.961 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-8.71 + 14.42 x))) fitted on training geodes: Brier score 0.032, expected calibration error 0.020 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 88 | 2.0% | 4.5% |
| 0.2-0.4 | 5 | 27.4% | 20.0% |
| 0.4-0.6 | 4 | 51.1% | 50.0% |
| 0.6-0.8 | 5 | 75.6% | 80.0% |
| 0.8-1.0 | 92 | 98.8% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 97.8% | 1.1% | 3.3% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 0.0% | n/a | 100.0% |

### H1b: Cluster share among large buds and clusters separates short from long exposure

Statement: Same as H1 but using clusters / (large buds + clusters), which ignores small and medium buds.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / (large + clusters) >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.93, false-positive rate 0.07, false-negative rate 0.08).
- Training: 401 samples; held-out test: 192 samples
- Test precision: 94.1% (95/101; 95% CI 87.6-97.2%)
- Test recall: 92.2% (95/103; 95% CI 85.4-96.0%)
- Test false-positive rate: 6.7% (6/89; 95% CI 3.1-13.9%)
- Test false-negative rate: 7.8% (8/103; 95% CI 4.0-14.6%)
- Balanced accuracy: test 0.927, training 0.917 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-13.51 + 16.32 x))) fitted on training geodes: Brier score 0.061, expected calibration error 0.025 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 74 | 3.0% | 2.7% |
| 0.2-0.4 | 14 | 29.6% | 28.6% |
| 0.4-0.6 | 6 | 53.9% | 83.3% |
| 0.6-0.8 | 13 | 75.1% | 84.6% |
| 0.8-1.0 | 85 | 93.5% | 95.3% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 180 | 91 | 93.3% | 6.7% | 6.6% |
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
- Calibration of a logistic model p = 1/(1+exp(-(5.82 + -5.97 x))) fitted on training geodes: Brier score 0.241, expected calibration error 0.004 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 0 | - | - |
| 0.4-0.6 | 73 | 46.8% | 46.6% |
| 0.6-0.8 | 0 | - | - |
| 0.8-1.0 | 4 | 97.2% | 100.0% |

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
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.54, false-positive rate 0.90, false-negative rate 0.01).
- Training: 414 samples; held-out test: 196 samples
- Test precision: 81.1% (154/190; 95% CI 74.9-86.0%)
- Test recall: 98.7% (154/156; 95% CI 95.4-99.6%)
- Test false-positive rate: 90.0% (36/40; 95% CI 76.9-96.0%)
- Test false-negative rate: 1.3% (2/156; 95% CI 0.4-4.6%)
- Balanced accuracy: test 0.544, training 0.594 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-2.05 + 0.55 x))) fitted on training geodes: Brier score 0.062, expected calibration error 0.043 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 21 | 16.3% | 23.8% |
| 0.2-0.4 | 11 | 28.0% | 9.1% |
| 0.4-0.6 | 23 | 48.0% | 43.5% |
| 0.6-0.8 | 11 | 71.0% | 90.9% |
| 0.8-1.0 | 130 | 98.9% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 143 | 79.7% | 92.3% | 0.7% |
| partial-scan | 2 | 1 | 50.0% | 0.0% | 100.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

## Cluster share versus exposure (sweep arm, complete observations)

| Random ticks | Hours at default speed | Samples | Mean cluster share | SD | Min | Max |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0 | 40 | 23.1% | 0.159 | 0.0% | 57.1% |
| 10 | 0.2 | 40 | 18.1% | 0.112 | 0.0% | 44.4% |
| 20 | 0.4 | 40 | 17.7% | 0.099 | 0.0% | 42.9% |
| 35 | 0.7 | 40 | 19.6% | 0.087 | 0.0% | 35.5% |
| 52 | 1.0 | 40 | 25.7% | 0.095 | 6.3% | 48.5% |
| 75 | 1.4 | 40 | 38.0% | 0.079 | 18.2% | 50.0% |
| 100 | 1.9 | 40 | 54.1% | 0.083 | 39.1% | 72.7% |
| 130 | 2.5 | 40 | 70.9% | 0.091 | 50.0% | 100.0% |
| 180 | 3.4 | 40 | 87.8% | 0.054 | 72.0% | 100.0% |
| 300 | 5.7 | 40 | 98.5% | 0.026 | 90.9% | 100.0% |
| 520 | 9.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 800 | 15.2 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 1300 | 24.7 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 2000 | 37.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |

Repeated observations: in 40 of 40 sweep geodes the cluster count never decreased between consecutive observations.

## Findings derived from the data

- The mean cluster share is not monotone in exposure: it fell from 23.1% at 0 to 18.1% at 10 random ticks. New small buds on free faces add to the denominator before any of them matures, so a higher share does not simply mean longer exposure.
- The share saturates: from 520 random ticks (about 9.9 h at default speed) the mean is 100.0%, so longer exposure leaves no further trace in this statistic.
- Freshly generated geodes already have a mean cluster share of 23.1% (range 0.0-57.1%) from world generation alone.
- H1: in the 'harvested-regrowth' condition 4 of 4 long-exposure geodes were missed (held-out set; small sample).
- H1b: in the 'harvested-regrowth' condition 2 of 4 long-exposure geodes were missed (held-out set; small sample).
- H3: in the 'partial-scan' condition 1 of 1 long-exposure geodes were missed (held-out set; small sample).
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

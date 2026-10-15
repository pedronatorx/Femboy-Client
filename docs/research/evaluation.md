# Amethyst growth as an exposure indicator: controlled evaluation

This report evaluates what amethyst growth-stage observations can and cannot tell about how long a geode's chunk was randomly ticked, in a controlled test world with known ground truth. It does not identify players or locate bases, and a correlation found here does not establish causation outside the tested setup.

## Setup

- Harvest arm: 12 geodes: 1300 random ticks, then 70% of clusters removed, then 26 more random ticks
- Exposure levels (random ticks): [0, 10, 20, 35, 52, 75, 100, 130, 180, 300, 520, 800, 1300, 2000]
- Sweep geodes: 40 (feature seeds 2001-2040)
- Geode center: 3987200, 42, -13966282
- Minecraft: 1.21.11
- World seed: 0
- Method: vanilla minecraft:amethyst_geode feature in a stone cube, growth by calling BlockState.randomTick on every budding block with a seeded RandomSource; observations by the analyzer's grouping code on server block data
- Samples: 656 observations; 596 used, 60 incomplete observations excluded
- Split by geode: 35 training geodes, 17 held-out test geodes (every third geode by id). Repeated observations of one geode never span both sets.
- Exposure unit: random ticks received by each budding block. One hour of a loaded, randomly ticking chunk at the default random tick speed (3) is 52.7 random ticks.
- Classifiers receive only observed features (stage counts, budding blocks, face occupancy). Ground truth is stored separately and is used only to score predictions.
- Pre-registered support criteria (held-out set): balanced accuracy >= 0.80, false-positive and false-negative rates <= 0.20, at least 30 test samples and 10 per class. Below 0.65 balanced accuracy a hypothesis is unsupported.

## Results by hypothesis

| Hypothesis | Verdict | Test samples (pos/neg) | Threshold | Precision | Recall | False-positive rate | False-negative rate |
|---|---|---|---|---|---|---|---|
| H1 Cluster share separates short from long exposure | **SUPPORTED** | 194 (103/91) | 0.650 | 98.0% | 95.1% | 2.2% | 4.9% |
| H1b Cluster share among large buds and clusters separates short from long exposure | **SUPPORTED** | 194 (103/91) | 0.871 | 95.8% | 88.3% | 4.4% | 11.7% |
| H2 Cluster share distinguishes durations beyond saturation | **UNSUPPORTED** | 77 (38/39) | 0.120 | 49.4% | 100.0% | 100.0% | 0.0% |
| H3 Any cluster indicates exposure (naive rule) | **UNSUPPORTED** | 194 (155/39) | 1.000 | 84.2% | 100.0% | 74.4% | 0.0% |

### H1: Cluster share separates short from long exposure

Statement: Geodes whose budding blocks were randomly ticked for at least 2 hours (at default speed) show a higher share of clusters among all growth objects than geodes with less exposure, enough to classify them on unseen geodes.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.96, false-positive rate 0.02, false-negative rate 0.05).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 98.0% (98/100; 95% CI 93.0-99.4%)
- Test recall: 95.1% (98/103; 95% CI 89.1-97.9%)
- Test false-positive rate: 2.2% (2/91; 95% CI 0.6-7.7%)
- Test false-negative rate: 4.9% (5/103; 95% CI 2.1-10.9%)
- Balanced accuracy: test 0.965, training 0.954 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-8.31 + 13.41 x))) fitted on training geodes: Brier score 0.032, expected calibration error 0.016 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 85 | 2.3% | 2.4% |
| 0.2-0.4 | 5 | 25.2% | 40.0% |
| 0.4-0.6 | 4 | 45.6% | 25.0% |
| 0.6-0.8 | 5 | 68.3% | 80.0% |
| 0.8-1.0 | 95 | 98.1% | 98.9% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 98.4% | 2.2% | 1.1% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 0.0% | n/a | 100.0% |

### H1b: Cluster share among large buds and clusters separates short from long exposure

Statement: Same as H1 but using clusters / (large buds + clusters), which ignores small and medium buds.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / (large + clusters) >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.92, false-positive rate 0.04, false-negative rate 0.12).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 95.8% (91/95; 95% CI 89.7-98.4%)
- Test recall: 88.3% (91/103; 95% CI 80.7-93.2%)
- Test false-positive rate: 4.4% (4/91; 95% CI 1.7-10.8%)
- Test false-negative rate: 11.7% (12/103; 95% CI 6.8-19.3%)
- Balanced accuracy: test 0.920, training 0.902 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-13.93 + 16.63 x))) fitted on training geodes: Brier score 0.050, expected calibration error 0.048 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 79 | 3.9% | 1.3% |
| 0.2-0.4 | 9 | 31.8% | 33.3% |
| 0.4-0.6 | 10 | 50.6% | 70.0% |
| 0.6-0.8 | 10 | 69.1% | 90.0% |
| 0.8-1.0 | 86 | 92.9% | 96.5% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 92.9% | 4.4% | 9.9% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 25.0% | n/a | 75.0% |

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
- Calibration of a logistic model p = 1/(1+exp(-(7.03 + -7.18 x))) fitted on training geodes: Brier score 0.240, expected calibration error 0.003 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 0 | - | - |
| 0.4-0.6 | 73 | 46.7% | 46.6% |
| 0.6-0.8 | 0 | - | - |
| 0.8-1.0 | 4 | 97.5% | 100.0% |

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
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.63, false-positive rate 0.74, false-negative rate 0.00).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 84.2% (155/184; 95% CI 78.3-88.8%)
- Test recall: 100.0% (155/155; 95% CI 97.6-100.0%)
- Test false-positive rate: 74.4% (29/39; 95% CI 58.9-85.4%)
- Test false-negative rate: 0.0% (0/155; 95% CI 0.0-2.4%)
- Balanced accuracy: test 0.628, training 0.506 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-2.90 + 0.58 x))) fitted on training geodes: Brier score 0.056, expected calibration error 0.039 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 31 | 10.0% | 12.9% |
| 0.2-0.4 | 15 | 31.6% | 46.7% |
| 0.4-0.6 | 7 | 49.5% | 71.4% |
| 0.6-0.8 | 13 | 71.0% | 84.6% |
| 0.8-1.0 | 128 | 99.2% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 143 | 84.1% | 74.4% | 0.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

## Cluster share versus exposure (sweep arm, complete observations)

| Random ticks | Hours at default speed | Samples | Mean cluster share | SD | Min | Max |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0 | 40 | 29.5% | 0.218 | 0.0% | 100.0% |
| 10 | 0.2 | 40 | 17.8% | 0.097 | 0.0% | 42.1% |
| 20 | 0.4 | 40 | 17.1% | 0.099 | 0.0% | 43.5% |
| 35 | 0.7 | 40 | 19.9% | 0.101 | 4.3% | 46.4% |
| 52 | 1.0 | 40 | 28.1% | 0.106 | 10.7% | 53.1% |
| 75 | 1.4 | 40 | 40.5% | 0.103 | 21.4% | 63.0% |
| 100 | 1.9 | 40 | 55.0% | 0.085 | 37.9% | 75.0% |
| 130 | 2.5 | 40 | 71.1% | 0.082 | 52.4% | 89.3% |
| 180 | 3.4 | 40 | 88.8% | 0.054 | 75.0% | 96.4% |
| 300 | 5.7 | 40 | 99.1% | 0.014 | 96.3% | 100.0% |
| 520 | 9.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 800 | 15.2 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 1300 | 24.7 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 2000 | 37.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |

Repeated observations: in 40 of 40 sweep geodes the cluster count never decreased between consecutive observations.

## Findings derived from the data

- The mean cluster share is not monotone in exposure: it fell from 29.5% at 0 to 17.8% at 10 random ticks. New small buds on free faces add to the denominator before any of them matures, so a higher share does not simply mean longer exposure.
- The share saturates: from 300 random ticks (about 5.7 h at default speed) the mean is 99.1%, so longer exposure leaves no further trace in this statistic.
- Freshly generated geodes already have a mean cluster share of 29.5% (range 0.0-100.0%) from world generation alone.
- H1: in the 'harvested-regrowth' condition 4 of 4 long-exposure geodes were missed (held-out set; small sample).
- H1b: in the 'harvested-regrowth' condition 3 of 4 long-exposure geodes were missed (held-out set; small sample).
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

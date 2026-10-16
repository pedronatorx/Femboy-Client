# Amethyst growth as an exposure indicator: controlled evaluation

This report evaluates what amethyst growth-stage observations can and cannot tell about how long a geode's chunk was randomly ticked, in a controlled test world with known ground truth. It does not identify players or locate bases, and a correlation found here does not establish causation outside the tested setup.

## Setup

- Exposure levels (random ticks): [0, 10, 20, 35, 52, 75, 100, 130, 180, 300, 520, 800, 1300, 2000]
- Harvest arm: 12 geodes: 1300 random ticks, then 70% of clusters removed, then 26 more random ticks
- Method: vanilla minecraft:amethyst_geode feature in a stone cube, growth by calling BlockState.randomTick on every budding block with a seeded RandomSource; observations by the analyzer's grouping code on server block data
- World seed: 0
- Minecraft: 1.21.11
- Geode center: -14804512, 42, 7854547
- Sweep geodes: 40 (feature seeds 2001-2040)
- Samples: 668 observations; 596 used, 72 incomplete observations excluded
- Split by geode: 35 training geodes, 17 held-out test geodes (every third geode by id). Repeated observations of one geode never span both sets.
- Exposure unit: random ticks received by each budding block. One hour of a loaded, randomly ticking chunk at the default random tick speed (3) is 52.7 random ticks.
- Classifiers receive only observed features (stage counts, budding blocks, face occupancy). Ground truth is stored separately and is used only to score predictions.
- Pre-registered support criteria (held-out set): balanced accuracy >= 0.80, false-positive and false-negative rates <= 0.20, at least 30 test samples and 10 per class. Below 0.65 balanced accuracy a hypothesis is unsupported.

## Results by hypothesis

| Hypothesis | Verdict | Test samples (pos/neg) | Threshold | Precision | Recall | False-positive rate | False-negative rate |
|---|---|---|---|---|---|---|---|
| H1 Cluster share separates short from long exposure | **SUPPORTED** | 194 (103/91) | 0.645 | 98.0% | 94.2% | 2.2% | 5.8% |
| H1b Cluster share among large buds and clusters separates short from long exposure | **SUPPORTED** | 194 (103/91) | 0.848 | 95.9% | 90.3% | 4.4% | 9.7% |
| H2 Cluster share distinguishes durations beyond saturation | **UNSUPPORTED** | 77 (38/39) | 0.286 | 49.4% | 100.0% | 100.0% | 0.0% |
| H3 Any cluster indicates exposure (naive rule) | **UNSUPPORTED** | 194 (155/39) | 1.000 | 80.3% | 100.0% | 97.4% | 0.0% |

### H1: Cluster share separates short from long exposure

Statement: Geodes whose budding blocks were randomly ticked for at least 2 hours (at default speed) show a higher share of clusters among all growth objects than geodes with less exposure, enough to classify them on unseen geodes.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.96, false-positive rate 0.02, false-negative rate 0.06).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 98.0% (97/99; 95% CI 92.9-99.4%)
- Test recall: 94.2% (97/103; 95% CI 87.9-97.3%)
- Test false-positive rate: 2.2% (2/91; 95% CI 0.6-7.7%)
- Test false-negative rate: 5.8% (6/103; 95% CI 2.7-12.1%)
- Balanced accuracy: test 0.960, training 0.967 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-8.57 + 14.41 x))) fitted on training geodes: Brier score 0.034, expected calibration error 0.030 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 86 | 2.2% | 4.7% |
| 0.2-0.4 | 3 | 33.7% | 0.0% |
| 0.4-0.6 | 5 | 47.5% | 20.0% |
| 0.6-0.8 | 8 | 73.4% | 75.0% |
| 0.8-1.0 | 92 | 98.7% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 97.8% | 2.2% | 2.2% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 0.0% | n/a | 100.0% |

### H1b: Cluster share among large buds and clusters separates short from long exposure

Statement: Same as H1 but using clusters / (large buds + clusters), which ignores small and medium buds.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / (large + clusters) >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.93, false-positive rate 0.04, false-negative rate 0.10).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 95.9% (93/97; 95% CI 89.9-98.4%)
- Test recall: 90.3% (93/103; 95% CI 83.0-94.6%)
- Test false-positive rate: 4.4% (4/91; 95% CI 1.7-10.8%)
- Test false-negative rate: 9.7% (10/103; 95% CI 5.4-17.0%)
- Balanced accuracy: test 0.929, training 0.929 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-15.59 + 19.15 x))) fitted on training geodes: Brier score 0.053, expected calibration error 0.012 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 71 | 2.7% | 1.4% |
| 0.2-0.4 | 18 | 28.0% | 27.8% |
| 0.4-0.6 | 6 | 44.6% | 50.0% |
| 0.6-0.8 | 9 | 71.0% | 77.8% |
| 0.8-1.0 | 90 | 96.1% | 96.7% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 93.4% | 4.4% | 8.8% |
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
- Calibration of a logistic model p = 1/(1+exp(-(7.35 + -7.50 x))) fitted on training geodes: Brier score 0.238, expected calibration error 0.001 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 0 | - | - |
| 0.4-0.6 | 73 | 46.6% | 46.6% |
| 0.6-0.8 | 0 | - | - |
| 0.8-1.0 | 4 | 98.5% | 100.0% |

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
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.51, false-positive rate 0.97, false-negative rate 0.00).
- Training: 402 samples; held-out test: 194 samples
- Test precision: 80.3% (155/193; 95% CI 74.1-85.3%)
- Test recall: 100.0% (155/155; 95% CI 97.6-100.0%)
- Test false-positive rate: 97.4% (38/39; 95% CI 86.8-99.5%)
- Test false-negative rate: 0.0% (0/155; 95% CI 0.0-2.4%)
- Balanced accuracy: test 0.513, training 0.519 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-5.85 + 1.15 x))) fitted on training geodes: Brier score 0.043, expected calibration error 0.024 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 29 | 4.4% | 6.9% |
| 0.2-0.4 | 9 | 22.4% | 33.3% |
| 0.4-0.6 | 3 | 47.8% | 66.7% |
| 0.6-0.8 | 8 | 74.3% | 62.5% |
| 0.8-1.0 | 145 | 99.7% | 98.6% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 143 | 79.1% | 97.4% | 0.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

## Cluster share versus exposure (sweep arm, complete observations)

| Random ticks | Hours at default speed | Samples | Mean cluster share | SD | Min | Max |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0 | 40 | 23.5% | 0.150 | 0.0% | 60.0% |
| 10 | 0.2 | 40 | 15.0% | 0.079 | 0.0% | 31.3% |
| 20 | 0.4 | 40 | 14.8% | 0.064 | 4.5% | 27.3% |
| 35 | 0.7 | 40 | 18.3% | 0.071 | 8.3% | 44.4% |
| 52 | 1.0 | 40 | 25.6% | 0.067 | 15.4% | 46.7% |
| 75 | 1.4 | 40 | 39.8% | 0.075 | 23.4% | 53.3% |
| 100 | 1.9 | 40 | 53.6% | 0.081 | 37.8% | 72.0% |
| 130 | 2.5 | 40 | 69.7% | 0.062 | 55.2% | 82.5% |
| 180 | 3.4 | 40 | 89.0% | 0.046 | 76.7% | 97.3% |
| 300 | 5.7 | 40 | 99.4% | 0.011 | 96.4% | 100.0% |
| 520 | 9.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 800 | 15.2 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 1300 | 24.7 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 2000 | 37.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |

Repeated observations: in 40 of 40 sweep geodes the cluster count never decreased between consecutive observations.

## Findings derived from the data

- The mean cluster share is not monotone in exposure: it fell from 23.5% at 0 to 15.0% at 10 random ticks. New small buds on free faces add to the denominator before any of them matures, so a higher share does not simply mean longer exposure.
- The share saturates: from 300 random ticks (about 5.7 h at default speed) the mean is 99.4%, so longer exposure leaves no further trace in this statistic.
- Freshly generated geodes already have a mean cluster share of 23.5% (range 0.0-60.0%) from world generation alone.
- H1: in the 'harvested-regrowth' condition 4 of 4 long-exposure geodes were missed (held-out set; small sample).
- H1b: in the 'harvested-regrowth' condition 2 of 4 long-exposure geodes were missed (held-out set; small sample).
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

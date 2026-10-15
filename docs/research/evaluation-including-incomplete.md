# Same evaluation including incomplete observations

This report evaluates what amethyst growth-stage observations can and cannot tell about how long a geode's chunk was randomly ticked, in a controlled test world with known ground truth. It does not identify players or locate bases, and a correlation found here does not establish causation outside the tested setup.

## Setup

- Geode center: 11730416, 42, 3523878
- Sweep geodes: 40 (feature seeds 2001-2040)
- Exposure levels (random ticks): [0, 10, 20, 35, 52, 75, 100, 130, 180, 300, 520, 800, 1300, 2000]
- Harvest arm: 12 geodes: 1300 random ticks, then 70% of clusters removed, then 26 more random ticks
- Method: vanilla minecraft:amethyst_geode feature in a stone cube, growth by calling BlockState.randomTick on every budding block with a seeded RandomSource; observations by the analyzer's grouping code on server block data
- World seed: 0
- Minecraft: 1.21.11
- Samples: 668 observations; 668 used, 0 incomplete observations excluded
- Split by geode: 35 training geodes, 17 held-out test geodes (every third geode by id). Repeated observations of one geode never span both sets.
- Exposure unit: random ticks received by each budding block. One hour of a loaded, randomly ticking chunk at the default random tick speed (3) is 52.7 random ticks.
- Classifiers receive only observed features (stage counts, budding blocks, face occupancy). Ground truth is stored separately and is used only to score predictions.
- Pre-registered support criteria (held-out set): balanced accuracy >= 0.80, false-positive and false-negative rates <= 0.20, at least 30 test samples and 10 per class. Below 0.65 balanced accuracy a hypothesis is unsupported.

## Results by hypothesis

| Hypothesis | Verdict | Test samples (pos/neg) | Threshold | Precision | Recall | False-positive rate | False-negative rate |
|---|---|---|---|---|---|---|---|
| H1 Cluster share separates short from long exposure | **SUPPORTED** | 196 (105/91) | 0.670 | 100.0% | 92.4% | 0.0% | 7.6% |
| H1b Cluster share among large buds and clusters separates short from long exposure | **SUPPORTED** | 196 (105/91) | 0.826 | 92.2% | 90.5% | 8.8% | 9.5% |
| H2 Cluster share distinguishes durations beyond saturation | **UNSUPPORTED** | 77 (38/39) | 0.167 | 49.4% | 100.0% | 100.0% | 0.0% |
| H3 Any cluster indicates exposure (naive rule) | **UNSUPPORTED** | 214 (165/49) | 1.000 | 80.1% | 95.2% | 79.6% | 4.8% |

### H1: Cluster share separates short from long exposure

Statement: Geodes whose budding blocks were randomly ticked for at least 2 hours (at default speed) show a higher share of clusters among all growth objects than geodes with less exposure, enough to classify them on unseen geodes.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / all growth objects >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.96, false-positive rate 0.00, false-negative rate 0.08).
- Training: 405 samples; held-out test: 196 samples
- Test precision: 100.0% (97/97; 95% CI 96.2-100.0%)
- Test recall: 92.4% (97/105; 95% CI 85.7-96.1%)
- Test false-positive rate: 0.0% (0/91; 95% CI 0.0-4.1%)
- Test false-negative rate: 7.6% (8/105; 95% CI 3.9-14.3%)
- Balanced accuracy: test 0.962, training 0.958 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-7.85 + 13.32 x))) fitted on training geodes: Brier score 0.034, expected calibration error 0.044 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 82 | 2.4% | 3.7% |
| 0.2-0.4 | 5 | 28.1% | 20.0% |
| 0.4-0.6 | 8 | 51.0% | 0.0% |
| 0.6-0.8 | 4 | 67.5% | 100.0% |
| 0.8-1.0 | 97 | 98.2% | 100.0% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 97.8% | 0.0% | 4.4% |
| partial-scan | 2 | 2 | 100.0% | n/a | 0.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 0.0% | n/a | 100.0% |

### H1b: Cluster share among large buds and clusters separates short from long exposure

Statement: Same as H1 but using clusters / (large buds + clusters), which ignores small and medium buds.

- Label: positive when exposure >= 105.5 random ticks (2.0 h)
- Rule: clusters / (large + clusters) >= threshold, threshold fitted on training geodes (Youden's J)
- Verdict: **SUPPORTED**. Meets all pre-registered criteria on held-out geodes (balanced accuracy 0.91, false-positive rate 0.09, false-negative rate 0.10).
- Training: 401 samples; held-out test: 196 samples
- Test precision: 92.2% (95/103; 95% CI 85.4-96.0%)
- Test recall: 90.5% (95/105; 95% CI 83.4-94.7%)
- Test false-positive rate: 8.8% (8/91; 95% CI 4.5-16.4%)
- Test false-negative rate: 9.5% (10/105; 95% CI 5.3-16.6%)
- Balanced accuracy: test 0.908, training 0.915 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-13.67 + 16.72 x))) fitted on training geodes: Brier score 0.069, expected calibration error 0.035 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 66 | 2.7% | 1.5% |
| 0.2-0.4 | 20 | 28.8% | 35.0% |
| 0.4-0.6 | 9 | 48.9% | 22.2% |
| 0.6-0.8 | 11 | 72.9% | 90.9% |
| 0.8-1.0 | 90 | 94.9% | 94.4% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 91 | 91.8% | 8.8% | 7.7% |
| partial-scan | 2 | 2 | 100.0% | n/a | 0.0% |
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
- Calibration of a logistic model p = 1/(1+exp(-(7.70 + -7.84 x))) fitted on training geodes: Brier score 0.239, expected calibration error 0.001 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 0 | - | - |
| 0.4-0.6 | 73 | 46.6% | 46.6% |
| 0.6-0.8 | 0 | - | - |
| 0.8-1.0 | 4 | 98.9% | 100.0% |

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
- Verdict: **UNSUPPORTED**. Performance on held-out geodes is close to chance (balanced accuracy 0.58, false-positive rate 0.80, false-negative rate 0.05).
- Training: 454 samples; held-out test: 214 samples
- Test precision: 80.1% (157/196; 95% CI 74.0-85.1%)
- Test recall: 95.2% (157/165; 95% CI 90.7-97.5%)
- Test false-positive rate: 79.6% (39/49; 95% CI 66.4-88.5%)
- Test false-negative rate: 4.8% (8/165; 95% CI 2.5-9.3%)
- Balanced accuracy: test 0.578, training 0.637 (a large gap would indicate overfitting)
- Calibration of a logistic model p = 1/(1+exp(-(-1.17 + 0.38 x))) fitted on training geodes: Brier score 0.087, expected calibration error 0.029 on test geodes

| Predicted probability | Test samples | Mean predicted | Observed positive rate |
|---|---:|---:|---:|
| 0.0-0.2 | 0 | - | - |
| 0.2-0.4 | 42 | 30.6% | 26.2% |
| 0.4-0.6 | 20 | 53.1% | 40.0% |
| 0.6-0.8 | 12 | 72.1% | 66.7% |
| 0.8-1.0 | 140 | 97.8% | 98.6% |

Performance by experimental condition (held-out geodes):

| Condition | Samples | Positives | Accuracy | False-positive rate | False-negative rate |
|---|---:|---:|---:|---:|---:|
| sweep | 182 | 143 | 78.6% | 100.0% | 0.0% |
| partial-scan | 20 | 10 | 60.0% | 0.0% | 80.0% |
| harvest-baseline | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested | 4 | 4 | 100.0% | n/a | 0.0% |
| harvested-regrowth | 4 | 4 | 100.0% | n/a | 0.0% |

## Cluster share versus exposure (sweep arm, complete observations)

| Random ticks | Hours at default speed | Samples | Mean cluster share | SD | Min | Max |
|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0 | 40 | 21.7% | 0.152 | 0.0% | 60.0% |
| 10 | 0.2 | 40 | 16.2% | 0.103 | 0.0% | 41.2% |
| 20 | 0.4 | 40 | 14.7% | 0.091 | 0.0% | 38.5% |
| 35 | 0.7 | 40 | 17.1% | 0.084 | 0.0% | 37.0% |
| 52 | 1.0 | 40 | 23.9% | 0.079 | 6.7% | 39.3% |
| 75 | 1.4 | 40 | 36.1% | 0.086 | 17.1% | 53.8% |
| 100 | 1.9 | 40 | 54.3% | 0.099 | 27.0% | 69.2% |
| 130 | 2.5 | 40 | 69.3% | 0.071 | 51.3% | 80.6% |
| 180 | 3.4 | 40 | 88.6% | 0.063 | 73.7% | 100.0% |
| 300 | 5.7 | 40 | 99.5% | 0.011 | 96.7% | 100.0% |
| 520 | 9.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 800 | 15.2 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 1300 | 24.7 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |
| 2000 | 37.9 | 40 | 100.0% | 0.000 | 100.0% | 100.0% |

Repeated observations: in 40 of 40 sweep geodes the cluster count never decreased between consecutive observations.

## Findings derived from the data

- The mean cluster share is not monotone in exposure: it fell from 21.7% at 0 to 16.2% at 10 random ticks. New small buds on free faces add to the denominator before any of them matures, so a higher share does not simply mean longer exposure.
- The share saturates: from 300 random ticks (about 5.7 h at default speed) the mean is 99.5%, so longer exposure leaves no further trace in this statistic.
- Freshly generated geodes already have a mean cluster share of 21.7% (range 0.0-60.0%) from world generation alone.
- H1: in the 'harvested-regrowth' condition 4 of 4 long-exposure geodes were missed (held-out set; small sample).
- H1b: in the 'harvested-regrowth' condition 3 of 4 long-exposure geodes were missed (held-out set; small sample).
- H3: in the 'partial-scan' condition 8 of 10 long-exposure geodes were missed (held-out set; small sample).
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

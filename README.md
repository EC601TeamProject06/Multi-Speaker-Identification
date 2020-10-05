# Multi-Speaker-Identification

## Define your product mission

  Speaker recognition is defined as identifying a person based on his/her voice characteristics. This is useful in applications for authentication to identify authorized users, for example, enable access control using voice of an individual. Most of times there are scenarios where multiple speakers speak simultaneously, single speaker identification systems fail to handle such audio signals. Therefore, it is significant to make the speaker recognition systems to handle multi-speaker audoi files and classify them.

## The user stories

* As a developer, I want to identify a person based on his/her voice characteristics.

* As a developer, I want to identify multi-speaker and classify them.

* As a developer, I want to identify the particular speaker within multi-speaker evironment.

## Define MVP 

* Tensorflow Platform.

* A python development environment.

* A device that can record voices. 


## Technologies to evaluate
* Tensorfow

Most of the existing voice recognition is implemented on the server, which will bring the following two problems:

1) When the network is poor, it will cause a large delay and bring poor user experience.

2) When the traffic is large, a large amount of server resources will be occupied.

To solve the above two problems, we choose to implement the function of voice recognition on the client. This paper USES machine learning to identify human voices. The framework used is Google's tensorflowLite framework, which is as compact as its name shows. While ensuring the accuracy, the size of the frame is only about 300KB, and the model produced after compression is one-fourth that of the TensorFlow model

* The usage scenarios of voice recognition are as follows:

1) Audio and video quality analysis: identify whether there are phenomena such as human voice, silent call, howling, background noise, etc.

2) Identify specific voice: Identify whether it is a specific voice, used for voice unlock, remote identity authentication, etc.

3) Identify emotions: It is used to judge the speaker's emotions and states. The combination of voice print content and emotional information can effectively prevent voice print counterfeiting and physical coercion.

4) Gender recognition: Male voice or female voice can be identified.

* The algorithm

To identify the speaker, Mel Frequency Cepstral Coefficients (MFCC)In order for a computer to recognize audio data, we must first transfer the audio data from the time domain to the frequency domain, and then extract the features. Mel Frequency Cepstral Coefficients (MFCC) is widely used for this step.


